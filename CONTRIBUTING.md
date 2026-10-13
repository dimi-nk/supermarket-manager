# Contributing

Forks and improvements are welcome. Please send them back as pull requests, so everyone gets them from one place.

## Ground rules

- The project is licensed under the [MIT License](LICENSE). By opening a pull request you agree that your contribution
  is licensed under the same terms.
- Sign off every commit to certify the [Developer Certificate of Origin](https://developercertificate.org/):
  ```bash
  git commit -s -m "Add Sklavenitis adapter"
  ```
  This adds a `Signed-off-by: Your Name <you@example.com>` line, meaning you wrote the change or have the right to submit it.

## Making a change

1. Read [HANDOVER.md](HANDOVER.md) and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Each module is independent: keep
   cross-module contracts in `basket-core`.
2. `mvn verify` must pass. Add tests next to the code you change. Retailer adapters need a contract test over a recorded,
   trimmed real response (see `AbGrContractTest`).
3. Keep requests to third-party sites polite (throttled, cached, honest User-Agent). Never add code that bypasses bot
   detection or CAPTCHAs, or that places orders or payments without an explicit user confirmation.
4. Describe in the PR what you verified live and what you could not.

Ideas to pick up: [docs/SIMILAR-PROJECTS.md](docs/SIMILAR-PROJECTS.md#ideas-worth-adopting-rough-priority).
