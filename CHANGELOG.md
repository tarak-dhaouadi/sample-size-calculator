# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.0.0] - 2026-10-02

### Added
- 13 calculators: single proportion, two independent proportions, paired proportions,
  single mean, two independent means, paired differences, Pearson and Spearman correlation,
  hazard ratio, log-rank test, one-way ANOVA, linear regression and diagnostic accuracy.
- Equality, non-inferiority, superiority and equivalence designs for two proportions / two means.
- Adjustments: continuity correction, finite population, clustering (ICC or design effect),
  response rate, t-distribution.
- Numerical test suite checked against published reference values, plus a randomised
  robustness test of the engine and of every calculator panel.
- GitHub Actions workflows (build and test on Linux and Windows, release on tag).

### Changed
- Rewritten in Java (Swing) with a resizable layout, input validation and clear error messages
  instead of crashes.
