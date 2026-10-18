# AetherX online presence setup

The app uses Firebase Anonymous Authentication plus Realtime Database presence.
Each installation gets a Firebase anonymous UID; the app only writes its own
`presence/{uid}` record. Realtime Database `onDisconnect()` removes the presence record if the app
process or network connection disappears unexpectedly. Leaving the app also
removes its record, so old offline installations do not accumulate.

## One-time Firebase Console setup

1. In **Authentication → Sign-in method**, enable **Anonymous**.
2. In **Realtime Database → Rules**, merge the contents of
   `online_presence.rules.fragment.json` into the existing root `rules` object.
   Do not replace unrelated rules used by `devices`, licenses, updates, or other
   existing features; keep those entries and merge the `presence` entry beside them.
3. Publish the merged rules and make sure the app's Firebase project is the
   same project that contains this Realtime Database.

The `presence` read permission is available to signed-in (including anonymous)
clients so every installation can calculate the aggregate online count. Writes
are restricted to the caller's own anonymous UID. Only an online state and server-side
last-change timestamp are stored; no display name or device identifier is stored
in presence records.
