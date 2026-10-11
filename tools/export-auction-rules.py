import argparse
import hashlib
import json
import re
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from html import escape
from pathlib import Path
from xml.etree import ElementTree
from zipfile import ZIP_DEFLATED, ZipFile


ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "docs/LUAT_DAU_GIA_BTL16.md"
OUTPUT = ROOT / "docs/LUAT_DAU_GIA_BTL16.docx"
MANIFEST = ROOT / "docs/LUAT_DAU_GIA_BTL16.sources.json"
DOCUMENT_PROFILE = "natural-language-auction-rules"
WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"


def digest(content):
    return hashlib.sha256(content).hexdigest()


def source_state():
    paths = sorted(ROOT.glob("src/**/*.java"))
    paths += [ROOT / "config/server.properties", ROOT / "sql/00_schema.sql", SOURCE,
              Path(__file__).resolve(), ROOT / "scripts/run-jdbc-tests.cmd"]
    files = {path.relative_to(ROOT).as_posix(): digest(path.read_bytes()) for path in paths}
    fingerprint = digest(json.dumps(files, sort_keys=True).encode("utf-8"))
    return files, fingerprint


def parameters():
    rules = (ROOT / "src/main/java/vn/ptit/btl16/server/auction/service/AuctionRules.java").read_text(encoding="utf-8")
    constants = dict(re.findall(r"public static final int (\w+) = (\d+);", rules))
    constants["MAX_MONEY"] = re.search(r'MAX_MONEY = new BigDecimal\("([^\"]+)"\)', rules).group(1)
    config = {}
    for line in (ROOT / "config/server.properties").read_text(encoding="utf-8").splitlines():
        if line and not line.lstrip().startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            if key.startswith(("auction.", "session.", "security.")):
                config[key.strip()] = value.strip()
    errors = (ROOT / "src/main/java/vn/ptit/btl16/common/protocol/ErrorCode.java").read_text(encoding="utf-8")
    enum_body = re.search(r"enum ErrorCode\s*\{(.*?)\}", errors, re.S).group(1)
    codes = re.findall(r"\b[A-Z][A-Z_]+\b", enum_body)
    return constants, config, list(dict.fromkeys(codes))


def paragraph(text, style="Normal"):
    runs = []
    for fragment in re.split(r"(\*\*[^*]+\*\*)", text):
        properties = ""
        if fragment.startswith("**") and fragment.endswith("**"):
            fragment = fragment[2:-2]
            properties = "<w:b/>"
        runs.append(f'<w:r><w:rPr>{properties}</w:rPr><w:t xml:space="preserve">{escape(fragment)}</w:t></w:r>')
    return f'<w:p><w:pPr><w:pStyle w:val="{style}"/></w:pPr>{"".join(runs)}</w:p>'


def render(markdown, constants, config):
    values = constants | config
    formatted_money = format(Decimal(constants["MAX_MONEY"]), ",.2f")
    values["MAX_MONEY"] = formatted_money.translate(str.maketrans({",": ".", ".": ","})).rstrip("0").rstrip(",")
    markdown = re.sub(r"\{\{([^{}]+)\}\}", lambda match: values[match.group(1)], markdown)
    date = datetime.now(timezone(timedelta(hours=7))).strftime("%d/%m/%Y")
    paragraphs = []
    for line in markdown.splitlines():
        if not line.strip():
            continue
        if line.startswith("# "):
            paragraphs.append(paragraph(line[2:], "Title"))
            paragraphs.append(paragraph(f"Bản ngày {date}", "Subtitle"))
        elif line.startswith("## "):
            paragraphs.append(paragraph(line[3:], "Heading1"))
        elif line.startswith("### "):
            paragraphs.append(paragraph(line[4:], "Heading2"))
        elif line.startswith("- "):
            paragraphs.append(paragraph("\u2022 " + line[2:]))
        else:
            paragraphs.append(paragraph(line))
    section = '<w:sectPr><w:footerReference w:type="default" r:id="rId2"/>' \
              '<w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" ' \
              'w:bottom="1134" w:left="1417" w:header="567" w:footer="567"/></w:sectPr>'
    document = f'<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="{WORD_NS}" xmlns:r="{REL_NS}">' \
               f'<w:body>{"".join(paragraphs)}{section}</w:body></w:document>'
    return document


def styles():
    body = '<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman"/>' \
           '<w:sz w:val="24"/><w:lang w:val="vi-VN"/></w:rPr></w:rPrDefault>' \
           '<w:pPrDefault><w:pPr><w:spacing w:after="120" w:line="276" w:lineRule="auto"/>' \
           '</w:pPr></w:pPrDefault></w:docDefaults>'
    for name, size in (("Normal", 24), ("Title", 40), ("Subtitle", 24), ("Heading1", 30), ("Heading2", 26)):
        heading = name.startswith("Heading")
        properties = '<w:keepNext/><w:spacing w:before="240" w:after="120"/>' if heading else ""
        if heading:
            properties += f'<w:outlineLvl w:val="{int(name[-1]) - 1}"/>'
        font = "Times New Roman"
        bold = "<w:b/><w:color w:val=\"1B453B\"/>" if heading or name == "Title" else ""
        body += f'<w:style w:type="paragraph" w:styleId="{name}"><w:name w:val="{name}"/>' \
                f'<w:pPr>{properties}</w:pPr><w:rPr><w:rFonts w:ascii="{font}" w:hAnsi="{font}"/>' \
                f'<w:sz w:val="{size}"/>{bold}</w:rPr></w:style>'
    return f'<?xml version="1.0" encoding="UTF-8"?><w:styles xmlns:w="{WORD_NS}">{body}</w:styles>'


def write_docx(document):
    content_types = '<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">' \
                    '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>' \
                    '<Default Extension="xml" ContentType="application/xml"/>'
    for part, kind in (("document", "document.main"), ("styles", "styles"), ("footer1", "footer")):
        content_types += f'<Override PartName="/word/{part}.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.{kind}+xml"/>'
    content_types += "</Types>"
    root_rels = f'<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="{REL_NS}/officeDocument" Target="word/document.xml"/></Relationships>'
    doc_rels = f'<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="{REL_NS}/styles" Target="styles.xml"/><Relationship Id="rId2" Type="{REL_NS}/footer" Target="footer1.xml"/></Relationships>'
    footer = f'<w:ftr xmlns:w="{WORD_NS}"><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:t xml:space="preserve">BTL16 | Trang </w:t></w:r><w:fldSimple w:instr=" PAGE "><w:r><w:t>1</w:t></w:r></w:fldSimple></w:p></w:ftr>'
    parts = {"[Content_Types].xml": content_types, "_rels/.rels": root_rels, "word/document.xml": document,
             "word/styles.xml": styles(), "word/_rels/document.xml.rels": doc_rels, "word/footer1.xml": footer}
    with ZipFile(OUTPUT, "w", ZIP_DEFLATED) as archive:
        for name, content in parts.items():
            ElementTree.fromstring(content)
            archive.writestr(name, content.encode("utf-8"))


def validate():
    files, fingerprint = source_state()
    metadata = json.loads(MANIFEST.read_text(encoding="utf-8"))
    if metadata.get("document_profile") != DOCUMENT_PROFILE:
        raise SystemExit("Rules document uses an outdated profile. Export it again.")
    if metadata["fingerprint"] != fingerprint or metadata["files"] != files:
        raise SystemExit("Rules document is stale: source changed. Export it again.")
    if metadata["docx_sha256"] != digest(OUTPUT.read_bytes()):
        raise SystemExit("DOCX changed after export; reconcile the document and source first.")
    with ZipFile(OUTPUT) as archive:
        if archive.testzip() is not None:
            raise SystemExit("Invalid DOCX ZIP member")
        for name in archive.namelist():
            ElementTree.fromstring(archive.read(name))
        document = archive.read("word/document.xml").decode("utf-8")
        if "{{" in document or "\ufffd" in document:
            raise SystemExit("Incomplete or invalid document text")
        text = " ".join(ElementTree.fromstring(document).itertext())
        technical_terms = ("Source fingerprint:", "Fingerprint:", ".java", "server.properties",
                           "JDBC", "WebSocket", "BigDecimal", "SELECT", "sessionToken",
                           "productId", "BID_ALREADY_LEADING", "--check")
        if any(term in text for term in technical_terms) or fingerprint in text:
            raise SystemExit("Technical implementation details must not appear in the reader's rules document.")
    print("[PASS] DOCX structure, UTF-8, source fingerprint and output checksum")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if not args.check:
        files, fingerprint = source_state()
        constants, config, codes = parameters()
        document = render(SOURCE.read_text(encoding="utf-8"), constants, config)
        write_docx(document)
        metadata = {"document_profile": DOCUMENT_PROFILE, "fingerprint": fingerprint,
                    "files": files, "constants": constants,
                    "config": config, "error_codes": codes, "docx_sha256": digest(OUTPUT.read_bytes()),
                    "exported_at": datetime.now(timezone(timedelta(hours=7))).isoformat()}
        MANIFEST.write_text(json.dumps(metadata, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")
        print("Created docs/LUAT_DAU_GIA_BTL16.docx")
    validate()


if __name__ == "__main__":
    main()
