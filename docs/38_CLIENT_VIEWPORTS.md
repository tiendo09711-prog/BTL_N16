# Client viewport and back-navigation audit

## Shared behavior

- `DesktopTheme.dialog` removes content-derived minimum dialog sizes, reuses
  existing vertical viewports, and adds a content-only viewport to plain forms.
  Dialog action bars remain outside the scrolling content.
- Dialogs fit the owning monitor's work area, excluding the taskbar, after their
  first layout without disturbing an existing owner-centered position. Users can
  resize them afterward.
- `DesktopTheme.viewport` provides vertical and horizontal overflow fallbacks for
  full pages and standalone windows without scaling down text or controls.

## Audited client surfaces

| Surface | Overflow behavior | Return action |
| --- | --- | --- |
| Login | Scroll the complete form on short displays | Existing sign-in/register actions |
| Main navigation | Scroll the sidebar without losing create/logout actions | Main navigation |
| Auction creation/review | Scroll fields; keep action bar and validation feedback visible | Back to owner; back to draft without losing input |
| Profile/register/password and social dialogs | Shared dialog viewport fallback | Cancel/close outside scrolling content |
| Products, wallet, transactions, receipts, rules | Scroll page content below the fixed main header | Back in main header |
| Joined auction room | Existing room viewports plus page overflow fallback | Back uses the existing leave-room request |
| Product/media editor | Separate form and preview/tool viewports; fixed save footer | Back closes the editor and retains draft cleanup |
| Media gallery | Scrollable gallery inside its window | Explicit back button |
| Standalone wallet, receipts, notifications, ranking, rules | Root viewport fallback | Existing close actions |
| Legacy Swing client | Scrollable login/auction cards; initial size fits work area | Existing navigation/dialog actions |

## Regression checks

Run `npm run test:full`. `ClientViewportUiSelfTest` exercises initial work-area
placement, window heights of 650/560/480 px, scrolling to the last field, visible
creation/review/save actions, fixed validation feedback, long alerts, draft
preservation, standalone account windows, and back navigation. Screenshots are
written to `target/client-viewport-audit`. The remaining UI self-tests cover the
existing account, media, notification, auction-room, and server workflows.
