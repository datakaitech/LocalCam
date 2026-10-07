# Security policy

LocalCam is a development preview. It has not had an independent security audit. See [the security and release review](SECURITY.txt) for implemented protections, trust boundaries and release gates.

Please do not post an exploitable vulnerability, private camera footage, pairing credentials or signing material in a public issue. Contact DataKai privately through the contact channel at https://datakai.in and identify the report as a LocalCam security issue. Include the affected version, reproducible steps and impact with sensitive values removed. A dedicated security address and response-time commitment have not yet been established.

The latest development version is the maintenance target; older previews have no separate support commitment. Fixes should be retested with certificate mismatch, token revocation, origin/host validation, permission denial and foreground/background transitions as relevant.

Release signing keys must remain outside the repository and source archives. Debug APKs and unsigned bundles are development artifacts, not Play Store releases.
