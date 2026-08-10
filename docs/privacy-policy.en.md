# VATRadar Privacy Policy

Last updated: 2026-08-10

VATRadar ("the app") is a non-commercial companion app that reads and displays public data
from the VATSIM network. This document explains what the app does with information.

## Summary

**The app creates no account and collects no personally identifying information** — no name,
no email address, no phone number, no location. Everything you enter is stored on your device.

## Stored on your device

The following stays **on the device only** and is deleted when you uninstall the app.

| Item | Purpose |
|---|---|
| SimBrief ID (alias or Pilot ID) | Fetching flight plans you generated on SimBrief |
| VATSIM CID | Checking rolled routes and recording your flights |
| Rolled routes | Showing the challenge in progress |
| Watched positions (e.g. RKSI) | Alerting you when that position comes online |
| Language and theme settings | App appearance |
| Worldwide airport database | Route suggestions and map display (public data bundled with the app) |

## Sent off the device

| Recipient | What is sent | Why |
|---|---|---|
| VATSIM (`data.vatsim.net`, `my.vatsim.net`) | Nothing (read only) | Live traffic, controllers and events |
| VATSIM METAR, NOAA Aviation Weather | Airport ICAO codes | Weather lookups |
| SimBrief (`simbrief.com`) | The SimBrief ID you entered | Fetching that account's flight plan |
| Google Maps | Requests needed to draw the map | Map rendering |
| Firebase Cloud Messaging | Device registration token, watched position codes, VATSIM CID | Receiving controller-online and challenge alerts |
| Challenge watcher (Cloudflare Workers) | VATSIM CID, departure and arrival airports of the rolled route | Checking whether the flight was completed |

Your SimBrief ID is sent to SimBrief only if you entered one, and only when you tap
"Fetch generated OFP".

## My flights

VATSIM does not publish the departure and arrival airports of past flights. When you save your
CID, the watcher server reads the public feed and records flights **from that point onward**.
Past flights cannot be recovered.

The server keeps only the CID, callsign, departure and arrival airports, times, and last
position. Clearing your CID in Settings stops any further recording.

## Challenge verification

When you roll a route in the app, the watcher server is told **your VATSIM CID and that route's
departure and arrival airports** so it can tell whether you flew it.

- A VATSIM CID is already a public identifier published by VATSIM itself.
- The server stores no other information, and does not store FCM tokens.
- A watch record is **deleted as soon as the flight is confirmed**, and in any case **after 48 hours**.
- Clearing your VATSIM CID in Settings stops any further registration.

The list of challenges in progress stays on your device.

## Alerts

Turning on controller alerts subscribes the app to Firebase Cloud Messaging topics. What leaves
the device is **which positions you subscribed to** and the **FCM token identifying the device** —
nothing that identifies you as a person.

The alert server only reads VATSIM's public controller list and stores no per-user data.

## Permissions

| Permission | Why |
|---|---|
| Internet / network state | Fetching VATSIM, weather and map data |
| Notifications (Android 13+) | Showing controller-online alerts. Denying it leaves every other feature working |

The app never requests location permission.

## Copyright and sources

The sources and licences of the data the app uses are listed in the app under
**Settings → About → Sources and licences**. The control-area boundaries are a simplified version
of the VAT-Spy Data Project data and are offered under the same CC BY-SA 4.0 licence.

VATRadar is an unofficial app and is not affiliated with VATSIM.

## Advertising and analytics

The app shows no advertising and contains no behavioural analytics.

## Children

The app is not directed at children and does not collect age information.

## Deleting your data

Uninstalling the app deletes every setting stored on the device. To stop alerts, remove the
watched positions on the Alerts page or turn alerts off.

## Third-party services

Services the app relies on are governed by their own policies.

- Google Play services and Google Maps — https://policies.google.com/privacy
- Firebase — https://firebase.google.com/support/privacy
- VATSIM — https://vatsim.net/docs/policy/privacy-policy
- SimBrief — https://www.simbrief.com

## Changes

If this policy changes, the date at the top of this document is updated.

## Contact

juwon0913@soongsil.ac.kr
