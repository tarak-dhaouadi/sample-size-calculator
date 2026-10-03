# Statistical methods, assumptions and validation

This document states, for every calculator, the formula that is implemented, the
assumptions behind it and how it was checked. The calculation engine is in
[`Calc.java`](../src/main/java/io/github/tarakdhaouadi/samplesize/Calc.java) and the
distribution functions in
[`Stats.java`](../src/main/java/io/github/tarakdhaouadi/samplesize/Stats.java).
The same method and assumptions are repeated in the "Calculation details" shown by the
application for every result.

## Conventions

| Symbol | Meaning |
|---|---|
| α, 1 − β | significance level and power |
| z<sub>p</sub> | standard normal quantile, so z<sub>α</sub> uses 1 − α/2 for a two-sided test and 1 − α for a one-sided test |
| r | allocation ratio = n(reference) / n(test); r = 1 means equal groups |
| q = 1 − p | complement of a proportion |

* **Rounding.** The unadjusted size is rounded **up** to the next whole subject. The adjustments
  are then applied in this order: finite-population correction, clustering (design effect),
  response rate, and the result is rounded up again. Every intermediate value is shown in the
  details panel.
* **Two-group designs.** The test group has `n` subjects and the reference group `ceil(r · n)`.
* **Margins** for non-inferiority, superiority and equivalence are typed as positive numbers.
  A non-inferiority margin δ means the test group may be worse than the reference by up to δ
  (the outcome is assumed to be one where higher is better).
* **Normal approximations.** Unless stated otherwise the formulas are large-sample
  approximations. The application warns when they are likely to be poor (for example when
  n·p or n·(1 − p) is below 5 for a single proportion).

## Proportions

### Estimate a single proportion
`n = z²·p·q / d²` for an absolute precision d, or `n = z²·q / (p·ε²)` when the precision is
relative to p (ε = relative half-width). z uses the two-sided confidence level.
*Assumptions:* simple random sampling, Wald (normal) interval.

### Compare two independent proportions
With reference proportion p₀ and test proportion p₁, the test-group size is

`n = (z_α + z_β)² · (p₀q₀ / r + p₁q₁) / D²`

where the effective difference D is

| Hypothesis | D | z<sub>α</sub> | z<sub>β</sub> |
|---|---|---|---|
| Equality | p₁ − p₀ | two-sided or one-sided | z<sub>power</sub> |
| Non-inferiority | p₁ − p₀ + δ | one-sided | z<sub>power</sub> |
| Superiority | p₁ − p₀ − δ | one-sided | z<sub>power</sub> |
| Equivalence | δ − \|p₁ − p₀\| | one-sided (two one-sided tests) | z<sub>(1+power)/2</sub> |

The optional **continuity correction** (Fleiss, Tytun & Ury) is
`n_c = n/4 · (1 + √(1 + 2(r + 1) / (n·r·|D|)))²`.
The test group can also be specified through a difference, a relative risk or an odds ratio,
which are converted to p₁.

### Compare paired proportions (McNemar test)
With discordant proportions b (+ → −) and c (− → +), ψ = b + c and δ = c − b, the number of pairs is

`n = [ (z_α·√ψ + z_β·√(ψ − δ²)) / δ ]²` (Connor 1987), plus `1/|δ|` with the continuity correction.

If marginal proportions p₀, p₁ and the correlation ρ between pairs are given instead,
`b = p₀(1 − p₁) − ρ·√(p₀q₀p₁q₁)` and `c = b + p₁ − p₀`. Combinations that imply a negative or
impossible discordant proportion are rejected.

## Means

### Estimate a mean
`n = z²σ² / d²`. With the **t-distribution adjustment** n is increased iteratively:
`n = t²(1−α/2, n−1)·σ² / d²` until it no longer changes.
*Assumptions:* known or well-estimated σ, approximately normal data.

### Compare two independent means
`n = (z_α + z_β)² · (r + 1)/r · σ² / D²` for the test group, with D defined as in the table above
(D = difference in means, δ margin). With the t adjustment, the z values are replaced by t values with
`(r + 1)·n − 2` degrees of freedom and the equation is iterated to convergence.
*Assumptions:* common standard deviation σ, independent groups, normal data.

### Compare paired differences (paired t-test)
`n = ((z_α + z_β) / es)²` with `es = mean difference / SD of the differences`; with the t adjustment
the degrees of freedom are n − 1 and the equation is iterated.

## Correlations

### Pearson
Fisher z transformation: `n = ((z_α + z_β) / (atanh ρ − atanh ρ₀))² + 3`.
*Assumption:* approximately bivariate normal data.

### Spearman
Bonett & Wright (2000): `n = (1 + ρ²/2) · ((z_α + z_β) / atanh ρ)² + 3`, with H₀: ρ = 0.

## Survival

### Hazard ratio
Schoenfeld (1983) number of events:
`d = (z_α + z_β)² · (1 + r)² / (r · (ln HR)²)`.
The total sample size is `d / P`, where P is the overall probability of observing the event, entered by the user.
*Assumptions:* proportional hazards, log-rank or Cox test, no competing risks.

### Log-rank test
The user gives either the survival proportions S₀ and S₁ at the end of follow-up, or median survival
times m₀, m₁ with a follow-up time t (then `S = exp(−ln 2 · t / m)`).
`HR = ln S₁ / ln S₀`, event probabilities are `1 − S`, the number of events is the Schoenfeld value above and
`N = d / p̄` with `p̄ = (r·p₀ + p₁) / (1 + r)`.
*Assumptions:* exponential survival, everyone followed to the end of the study period, no other censoring
and no staggered accrual. For designs with accrual and loss to follow-up, use the Hazard Ratio calculator
with an event probability obtained from a more detailed projection.

## Other designs

### One-way ANOVA
Exact power from the non-central F distribution with `df₁ = k − 1`, `df₂ = N − k` and non-centrality
`λ = f²·N`, where N = k·n and Cohen's f = σ<sub>means</sub>/σ. When group means and a common SD are given,
`σ_means = √(Σ(μᵢ − μ̄)² / k)`. The smallest n per group reaching the requested power is returned.
*Assumptions:* equal group sizes, normal data, common variance.

### Linear regression (F test)
Exact power from the non-central F distribution with `df₁ = q` (predictors tested), `df₂ = N − p − 1`
(p = total predictors) and `λ = f²·N`, where `f² = R²_tested / (1 − R²_full)`. The smallest N reaching the
requested power is returned. *Assumption:* fixed-effects model as in G\*Power's "R² deviation from zero /
increase" tests.

### Diagnostic accuracy
Buderer (1996): the number of diseased subjects for sensitivity Se is `z²·Se(1 − Se)/d²`, divided by the
prevalence to give the total; the number of non-diseased for specificity Sp is `z²·Sp(1 − Sp)/d²`, divided by
`1 − prevalence`. When both are requested the larger total is returned. *Assumption:* normal approximation.

## Plots

Every calculator draws two plots from the same functions as the result, so the marked point always equals the
calculated sample size.

* **Sample size plot.** The sample size is computed on a grid of a key input and for three effect sizes:
  the current effect and two larger ones a round step apart (for example differences of 0.15, 0.20 and 0.25), so
  the first curve is the one that passes through the current result. For hazard-ratio designs the two stronger
  curves are hazard ratios further from 1; for correlation designs and the paired effect-size calculator the three
  curves are three power levels (the current power and the next two usual levels).

  | Calculator | x axis | Curves |
  |---|---|---|
  | Single proportion | expected proportion | precision |
  | Two proportions | proportion in the reference group | difference in proportions |
  | Paired proportions | proportion of discordant pairs (b + c) | difference c − b |
  | Single mean | standard deviation | precision |
  | Two means / paired differences | standard deviation | mean difference |
  | Pearson / Spearman | expected correlation | power level |
  | Hazard ratio | overall probability of the event | hazard ratio |
  | Log-rank | survival proportion in the reference group (S₁ = S₀<sup>HR</sup>) | hazard ratio |
  | ANOVA | number of groups | Cohen's f |
  | Regression | number of predictors tested | Cohen's f² |
  | Diagnostic accuracy | disease prevalence | precision |

  When a steep low-end tail would flatten the rest of the plot, the view zooms on the part within three times the
  current result and the curves run off the top of the frame. Points where the inputs are impossible (for example a
  test-group proportion above 1) are left out.
* **Power plot.** The sample size for powers from 0.50 to 0.99. For the estimation designs (single proportion,
  single mean, diagnostic accuracy), which have no power, the x axis is the confidence level from 0.80 to 0.99.
  The y axis is the size per group for two-group designs, the number of pairs for paired designs and the total
  sample size otherwise. All other inputs and adjustments are kept as entered.

## Validation

The tests in [`CalcTest.java`](../src/test/java/io/github/tarakdhaouadi/samplesize/CalcTest.java)
(run with `./build.sh test` or `build.bat test`) check the engine at four levels:

1. **Distribution functions** against standard tables (normal, Student t and F quantiles; the identity
   F(1, df) = t²).
2. **Reference sample sizes**: worked examples published on the help pages of
   [Statulator](https://statulator.com) (proportions and paired proportions), the usual textbook values
   (for example 385 subjects for p = 0.5 with ±5 %, 66 events for HR = 0.5) and G\*Power results
   (ANOVA 159 / 180 / 66 subjects, regression 77 and 55 subjects, two-sample t-test 394 / 64 / 26 per group
   for d = 0.2 / 0.5 / 0.8, paired t-test 90).
3. **Properties that must always hold** (more power or a smaller α needs more subjects, a larger effect needs
   fewer, one-sided needs fewer than two-sided, ratio r and 1/r need the same total, and so on).
4. **Plots**: the marked point equals the calculated result for every calculator, the power and confidence curves never
   decrease, and every plot can be drawn.
5. **Robustness**: thousands of random and extreme inputs must produce either a result or a clear
   input-error message, never a crash. `ModulesSmokeTest.java` does the same through every calculator panel.

### Known differences from Statulator

* Intermediate values are rounded up at each stage, so the finite-population example 323 → 279 where
  Statulator's help page shows about 278.
* For the t-adjusted single mean (σ = 18, d = 3) the application returns 141; the Statulator help page
  quotes both 139 and about 142 for this example.

## References

* Fleiss JL, Tytun A, Ury HK (1980). A simple approximation for calculating sample sizes for comparing
  independent proportions. *Biometrics* 36.
* Connor RJ (1987). Sample size for testing differences in proportions for the paired-sample design.
  *Biometrics* 43.
* Schoenfeld DA (1983). Sample-size formula for the proportional-hazards regression model. *Biometrics* 39.
* Bonett DG, Wright TA (2000). Sample size requirements for estimating Pearson, Kendall and Spearman
  correlations. *Psychometrika* 65.
* Buderer NMF (1996). Statistical methodology: I. Incorporating the prevalence of disease into the sample
  size calculation for sensitivity and specificity. *Academic Emergency Medicine* 3.
* Cohen J (1988). *Statistical Power Analysis for the Behavioral Sciences*, 2nd ed. Lawrence Erlbaum.
* Chow S-C, Shao J, Wang H (2008). *Sample Size Calculations in Clinical Research*, 2nd ed. Chapman & Hall/CRC.
* Faul F, Erdfelder E, Lang A-G, Buchner A (2007). G\*Power 3: a flexible statistical power analysis program.
  *Behavior Research Methods* 39.
