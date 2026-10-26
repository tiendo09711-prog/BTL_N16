# Live auction room

The JavaFX client keeps the existing auction rules and server protocol. The room
now includes a personal status banner, live watcher count, a price chart, loaded
history statistics, a countdown progress bar and a richer bid timeline.

## Demo flow

1. Start the server and connect a seller and two buyers. Fund the buyer demo
   wallets through the existing server approval flow.
2. Create an auction, join from the buyers and alternate accepted bids. Observe
   the price chart, price growth, personal leading/outbid states and highlighted
   timeline entries. Hover over a chart point to read its exact price and time.
3. Toggle the own-bids filter. It affects the timeline only, not the chart or the
   auction. Open history to use the existing server-authorized history dialog.
4. Observe the final 30-second warning and an extension confirmed by the server.
   Reaching zero locally displays a waiting state, not an invented winner.
5. After the server ends the auction, show the winner banner and wallet ledger.
   The existing 60-second result screen and automatic return remain unchanged.

## Data and permissions

- All points and timeline entries come from accepted bids or the authoritative
  opening/current price. There are no simulated bids or simulated participants.
- Counts describe the history currently loaded by the client, not an all-time
  bid count or a complete participant list. History is still server-limited.
- Watcher count is the room count supplied by the server, including the current
  client when applicable; it is not the number of distinct people who bid.
- Filtering never changes bid eligibility. The host and current leader still
  cannot bid; private-room and kick permissions stay server-authoritative.
- On disconnect the banner identifies the data as the last known state. Leaving
  or switching rooms clears the chart and own-history filter.
- Timer ticks update the countdown/presence without rebuilding the chart and
  timeline. The compact product header keeps the chart visible on laptop screens;
  media can still be opened at full size with the existing gallery button.

## Upgrade Demo (Clusters 4-6)

After the server confirms closure, use "Ket qua phien cua toi" to reopen the
personal receipt even after leaving, reconnecting or restarting. A leading bid
is not a win; a receipt is not permission to reopen private chat or other users'
profiles. Receipt and wallet policy: `30_KET_QUA_PHIEN.md`.

Lobby filters/sorting apply to the entire server snapshot before paging. Room
creation now requires a review and confirmation; access and identity choices
cannot be changed afterward. Product metadata/media freeze at the first room.
Persistent personal notifications do not grant room access. Details:
`31_HOAN_THIEN_CHUNG.md`. Demo and real-LAN checklist: `32_DEMO_VA_NGHIEM_THU.md`.

## Verification

`LiveAuctionRoomUiSelfTest` covers chart ordering, real data counts, filtering,
presence-only updates, personal roles, deadline/extension, confirmed outcomes,
room reset and compact layout. `DesktopUiSelfTest` checks controller integration
with a real isolated server. Both run as part of `npm run test:full`.

Preview images are written under `target/live-room-preview` and
`target/desktop-preview`. The standalone room test does not access a database;
integration tests use an isolated test database, never the production schema.

Rebuild the client distribution before demonstrating these changes in an EXE;
existing packaged executables do not pick up edits from source automatically.
