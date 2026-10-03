# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.1.0] - 2026-10-03

### Added
- Two plots for every one of the 13 designs, in two new tabs next to the result:
  - **Sample size plot**: sample size against a key input (for example the expected proportion in the
    reference group) with three curves for increasing effect sizes, and the current result marked;
  - **Power plot**: sample size against the power (against the confidence level for the estimation designs),
    with the current result marked.
- "Enlarge" and "Save as PNG..." buttons for each plot.

### Fixed
- A confidence level close to 0 could give a sample size of 0; at least one subject is now always returned.

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
