"""Regenerate bundled Vietnamese invitations with the local Piper model (no playback)."""
from pathlib import Path
import os
import subprocess
import wave


ROOT = Path(__file__).resolve().parent.parent
SPEECH = ROOT / "tools" / "speech"
PIPER = SPEECH / "piper"
OUTPUT = ROOT / "src" / "main" / "resources" / "audio" / "client"
PROMPTS = {
    "auction-closing-reminder.wav": "Có ai muốn đưa ra mức giá cao hơn không ạ?",
    "auction-bid-invitation.wav": "Có ai trả cao hơn không ạ?",
}


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for filename, text in PROMPTS.items():
        destination = OUTPUT / filename
        subprocess.run(
            [str(PIPER / ("piper.exe" if os.name == "nt" else "piper")),
             "--model", "../vi_VN-vais1000-medium.onnx",
             "--config", "../vi_VN-vais1000-medium.onnx.json",
             "--espeak_data", "espeak-ng-data",
             "--output_file", os.path.relpath(destination, PIPER),
             "--length_scale", "1.03", "--noise_scale", "0.75", "--noise_w", "0.85",
             "--sentence_silence", "0.18"],
            input=(text + "\n").encode("utf-8"), cwd=PIPER, check=True, timeout=30,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        )
        with wave.open(str(destination), "rb") as audio:
            assert audio.getnchannels() == 1 and audio.getsampwidth() == 2
            duration = audio.getnframes() / audio.getframerate()
            assert 1 < duration < 10, (filename, duration)
            print(f"[VOICE] {filename}: {duration:.2f}s, {audio.getframerate()}Hz")


if __name__ == "__main__":
    main()
