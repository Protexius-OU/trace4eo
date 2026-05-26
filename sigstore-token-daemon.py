#!/usr/bin/env python3
"""Sigstore OIDC token daemon.

On start: device flow against Sigstore's public OIDC, requesting offline_access
so we get a refresh_token. Writes the id_token to TOKEN_FILE (atomic rename),
then loops, refreshing the id_token before it expires.

The signing tool's OidcTokenResolver picks up SIGSTORE_ID_TOKEN from the
environment, so worker invocations just do `export SIGSTORE_ID_TOKEN=$(cat
~/.sigstore-id-token)` before calling create-provenance-record. No CLI changes.

Env:
  SIGSTORE_ISSUER       (default https://oauth2.sigstore.dev/auth)
  SIGSTORE_TOKEN_FILE   (default ~/.sigstore-id-token)
  REFRESH_LEAD_S        (default 10 — refresh this many seconds before expiry)
"""
import base64
import hashlib
import json
import os
import secrets
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ISSUER = os.environ.get("SIGSTORE_ISSUER", "https://oauth2.sigstore.dev/auth")
CLIENT_ID = "sigstore"
TOKEN_FILE = os.environ.get(
    "SIGSTORE_TOKEN_FILE", os.path.expanduser("~/.sigstore-id-token")
)
REFRESH_LEAD_S = int(os.environ.get("REFRESH_LEAD_S", "10"))


def b64url(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()


def post_form(url, data):
    req = urllib.request.Request(
        url,
        method="POST",
        data=urllib.parse.urlencode(data).encode(),
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode())
        except Exception:
            raise


def get_json(url):
    with urllib.request.urlopen(url, timeout=60) as r:
        return json.load(r)


def jwt_exp(token: str) -> int:
    payload = token.split(".")[1]
    payload += "=" * (-len(payload) % 4)
    claims = json.loads(base64.urlsafe_b64decode(payload))
    return int(claims["exp"])


def atomic_write(path: str, content: str) -> None:
    tmp = path + ".tmp"
    with open(tmp, "w") as f:
        f.write(content)
    os.chmod(tmp, 0o600)
    os.rename(tmp, path)


def device_flow(conf, scope: str):
    verifier = b64url(secrets.token_bytes(32))
    challenge = b64url(hashlib.sha256(verifier.encode()).digest())
    dc = post_form(
        conf["device_authorization_endpoint"],
        {
            "client_id": CLIENT_ID,
            "scope": scope,
            "code_challenge_method": "S256",
            "code_challenge": challenge,
        },
    )
    if "device_code" not in dc:
        print(f"Device authorization failed: {dc}", file=sys.stderr)
        sys.exit(1)
    uri = dc.get("verification_uri_complete") or dc["verification_uri"]
    print("\nAuthenticate at:\n", file=sys.stderr)
    print(f"  {uri}\n", file=sys.stderr)
    print(f"User code: {dc['user_code']}\n", file=sys.stderr)
    print("Waiting for authorization...", file=sys.stderr, flush=True)

    interval = dc.get("interval", 5)
    deadline = time.time() + dc["expires_in"]
    while time.time() < deadline:
        time.sleep(interval)
        r = post_form(
            conf["token_endpoint"],
            {
                "grant_type": "urn:ietf:params:oauth:grant-type:device_code",
                "device_code": dc["device_code"],
                "client_id": CLIENT_ID,
                "code_verifier": verifier,
            },
        )
        if "id_token" in r:
            print("Authorization successful", file=sys.stderr)
            return r
        err = r.get("error")
        if err == "authorization_pending":
            continue
        if err == "slow_down":
            interval += 5
            continue
        print(f"Device flow failed: {r}", file=sys.stderr)
        sys.exit(1)
    print("Device code expired without authorization", file=sys.stderr)
    sys.exit(1)


def refresh(conf, refresh_token: str):
    r = post_form(
        conf["token_endpoint"],
        {
            "grant_type": "refresh_token",
            "refresh_token": refresh_token,
            "client_id": CLIENT_ID,
        },
    )
    if "id_token" in r:
        return r
    print(f"Refresh failed: {r}", file=sys.stderr)
    return None


def write_token(id_token: str) -> int:
    atomic_write(TOKEN_FILE, id_token + "\n")
    exp = jwt_exp(id_token)
    remaining = exp - int(time.time())
    sleep_s = max(10, remaining - REFRESH_LEAD_S)
    print(
        f"Wrote {TOKEN_FILE} — valid for {remaining}s "
        f"(next refresh in {sleep_s}s)",
        file=sys.stderr,
        flush=True,
    )
    return sleep_s


def main():
    conf = get_json(f"{ISSUER}/.well-known/openid-configuration")

    # First try with offline_access (refresh tokens); if issuer doesn't grant
    # one, fall back to bare openid+email and just re-run device flow on expiry.
    state = device_flow(conf, scope="openid email offline_access")
    has_refresh = "refresh_token" in state
    if not has_refresh:
        print(
            "Note: issuer did not return a refresh_token — will re-prompt "
            "device flow on each expiry.",
            file=sys.stderr,
        )

    while True:
        sleep_s = write_token(state["id_token"])
        time.sleep(sleep_s)

        if has_refresh:
            refreshed = refresh(conf, state["refresh_token"])
            if refreshed is None:
                print("Refresh failed — re-running device flow", file=sys.stderr)
                state = device_flow(conf, scope="openid email offline_access")
                has_refresh = "refresh_token" in state
            else:
                if "refresh_token" not in refreshed:
                    refreshed["refresh_token"] = state["refresh_token"]
                state = refreshed
        else:
            state = device_flow(conf, scope="openid email")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nDaemon stopped.", file=sys.stderr)
