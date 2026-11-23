# App case studies: how fitness games and activity apps gamify activity goals, and what the evidence says

Researched 2026-11-23. Network note: pmc.ncbi.nlm.nih.gov and 9to5mac.com could not be fetched from this session (not on the allowed hosts list), so findings from those pages come from search-result abstracts and snippets, not full texts. Rows marked "(snippet)" should be checked against the full text before anyone quotes exact numbers.

## Q1. Mechanics per app: goal unit, daily or weekly, fixed or adaptive, missed days, rest and illness, decay or loss

### Takeaway
The major wearables have shifted toward **weekly minute-based goals** tied to the WHO/AHA 150-minute guideline: Fitbit Active Zone Minutes, Garmin intensity minutes and Google Heart Points. They have also added **explicit rest controls**. Apple added pause rings and per-weekday goals in watchOS 11 (2024). Fitbit's Daily Readiness turns a low-readiness day into a recovery suggestion rather than a failure. The games (Pokémon GO, Pikmin Bloom) reward distance or steps with **no penalty for missing**. Punitive decay shows up mainly in habit RPGs (Habitica HP damage) and in streaks (Apple, Duolingo). Both later added escape valves: the Inn and pause damage in Habitica, pause rings at Apple, and the streak freeze at Duolingo.

### Cited Findings

**Apple Watch Activity rings (Move kcal, Exercise min, Stand hours; daily; streaks and awards)**
- watchOS 11 lets users adjust Activity ring goals by day of the week, or pause them for a rest day, week, month or more without affecting the award streak. — [Apple support: About watchOS 11 Updates](https://support.apple.com/en-ie/121163)
- Announced at WWDC in June 2024 as part of watchOS 11's "powerful health and fitness insights". — [Apple Newsroom, June 2024](https://apple.com/newsroom/2024/06/watchos-11-brings-powerful-health-and-fitness-insights)
- Pause presets are "For Today", "Until Week", "Until Month" and Custom. They can be set from the Watch Activity app or the iPhone Fitness app. — [Beebom](https://beebom.com/how-pause-activity-rings-apple-watch/amp/)
- One source says a pause can last up to 90 days without losing the streak (snippet; not confirmed on Apple's page). — [iMore](https://www.imore.com/health-fitness/apple-watch/after-9-years-apple-has-finally-added-the-apple-watch-feature-weve-been-begging-for-rest-days-are-here-and-they-wont-break-your-award-streaks)
- The trade press framed this as rest days arriving "after 9 years" of user requests. The rings date from the 2015 launch. — [iMore](https://www.imore.com/health-fitness/apple-watch/after-9-years-apple-has-finally-added-the-apple-watch-feature-weve-been-begging-for-rest-days-are-here-and-they-wont-break-your-award-streaks); [heise: "sick and recovery days for the first time"](https://heise.de/-9955575)
- Apple still runs streak-style awards. Example: "Ring in the New Year" requires closing all three rings 7 days in a row in January. — [Apple Newsroom, Jan 2026](https://www.apple.com/mt/newsroom/2026/01/apple-watch-keeps-users-active-and-motivated-in-2026/)

**Fitbit / Google (daily steps; weekly Active Zone Minutes; badges; Daily Readiness)**
- The default AZM goal is **150 AZM per week**, about 22 per day, and users can change it. — [Google/Fitbit help](https://support.google.com/fitbit/answer/14236509)
- The default comes from the 150 min moderate or 75 min vigorous weekly guideline. Cardio and peak zone minutes count double, which mirrors that equivalence. — [Google Store: Active Zone Minutes](https://store.google.com/magazine/fitbit_active_zone_minutes?hl=en-US)
- **Daily Readiness** (Premium, launched November 2021) is a 1–100 score built from activity, sleep and HRV. It is paired each morning with a personalised AZM target based on the user's own averages. Low scores bring recovery suggestions (yoga, stretching, meditation) rather than a failure state. A score of 30 or above signals that exercise is appropriate. — [Google/Fitbit help](https://support.google.com/fitbit/answer/14236710); [Google blog](https://blog.google/products/fitbit/premium-daily-readiness/); [DC Rainmaker review, Nov 2021](https://dcrainmaker.com/2021/11/fitbit-readiness-review.html)
- On 27 March 2023 Fitbit **removed Challenges, Adventures and Open Groups**, announced in February 2023 to "streamline" the app. In-progress challenges ended and the trophies earned from them were lost. Users protested on the forums. — [9to5Google, 27 Mar 2023](https://9to5google.com/2023/03/27/fitbit-challenges-groups-removed/); [Engadget](https://engadget.com/fitbit-removing-open-groups-adventures-challenges-app-140024841.html); [Fitbit Community](https://community.fitbit.com/t5/Product-Feedback/Don-t-remove-Challenges-amp-Adventures/idi-p/5335785/page/49)
- The October 2023 app redesign showed a fixed 150-minute "Weekly Target" bar beside a user's custom AZM goal, which confused users. — [Fitbit Community, Oct 2023](https://community.fitbit.com/t5/Android-App/New-App-active-zone-mins-in-weekly-display/td-p/5465709)

**Google Fit Heart Points (minutes weighted by intensity; weekly guideline, daily setting)**
- Heart Points launched in August 2018, developed with the AHA and WHO. Moderate activity earns 1 point per minute and vigorous activity 2. **150 points a week** meets the guideline. — [AHA news, 21 Aug 2018](https://www.heart.org/en/news/2018/08/21/google-just-launched-heart-points-here-are-5-things-you-need-to-know); [Google Fit help](https://support.google.com/fit/answer/7619539)
- The user-set target in the app is per day, so about 22 a day matches 150 a week. — [Google Fit help](https://support.google.com/fit/answer/7619539)
- Google is folding Fit into Fitbit and the Health products. I found no 2025–2026 documentation of Fit's current behaviour (see Gaps).

**Garmin (auto-adjusting daily step goal; weekly intensity minutes)**
- "Auto Goal" lets the device set the daily step goal automatically from prior activity. Some manuals give a starting default of 7,500 steps, others 5,000. — [Garmin manual: About the Step Goal](https://www8.garmin.com/manuals/webhelp/vivoki/EN-US/GUID-B985DB46-F875-4DD3-9109-33DCBE6D9F64.html); [Garmin manual](https://www8.garmin.com/manuals/webhelp/vivosmart/EN-GB/GUID-3C2177B2-5BAE-4324-B709-8148220584D6.html)
- "Weekly Intensity Minutes" is a user-entered weekly goal for moderate-to-vigorous minutes. Goal alerts cover the daily steps goal and the weekly intensity minutes goal. — [Garmin Activity Tracking Settings](https://www8.garmin.com/manuals/webhelp/vivoactive4_4S/EN-US/GUID-018B4A1D-17C8-4F7C-8D10-1521D9D8FC9C.html)
- The exact auto-goal adjustment rule is not documented in Garmin's manuals. A Garmin forum user asks the same question without an official answer. — [Garmin forums](https://forums.garmin.com/apps-software/mobile-apps-web/f/garmin-connect-mobile-ios/247379/how-does-garmin-work-out-your-fail-steps-goal-it-varies-and-i-don-t-remember-setting-a-goal)

**Pokémon GO (distance; weekly Adventure Sync tiers; no penalty)**
- Adventure Sync (launched November 2018, Trainer level 5+) reads Apple Health or Google Fit in the background. A weekly fitness report pays rewards every Monday at 9:00 local time for **5, 25 and 50 km** walked in the previous seven days. A 100 km tier existed at some point. Rewards have changed several times. — [Niantic help: Adventure Sync](https://niantic.helpshift.com/hc/faq/3265-adventure-sync); [Bulbapedia](https://bulbapedia.bulbagarden.net:443/wiki/Adventure_Sync); [Mein-MMO on reward changes](https://mein-mmo.de/en/pokemon-go-changes-the-rewards-for-weekly-progress,297708)
- Missing a tier simply forfeits that week's bonus. Nothing already earned decays. Players complain that credited distance falls short of the distance their fitness app shows. — [Mein-MMO](https://mein-mmo.de/en/thats-why-you-have-to-walk-more-kilometers-than-intended-in-pokemon-go,321598)

**Pikmin Bloom (steps; cumulative progression; soft weekly group challenge)**
- Steps grow Pikmin seedlings and petals plant flowers along the route. A daily "lookback" replays the day's route with an optional journal entry. Level-ups are gated by cumulative steps, for example 14,000 steps for one level. — [Nintendo, 28 Oct 2021](https://nintendo.com/us/whatsnew/mobilenews-grow-and-thrive-with-pikmin-bloom-available-now-for-ios-and-android); [Pikmin wiki: Weekly challenge](https://www.pikminwiki.com/Weekly_challenge)
- Weekly challenges are group step goals. Even when the group misses the goal there is "a smaller participation prize", so failure is softened. — [Pikmin wiki](https://www.pikminwiki.com/Weekly_challenge)
- Daily-step events award badges and nothing is lost by missing them. — [Pikmin wiki](https://www.pikminwiki.com/Special:MobileDiff/288489...297840)

**Duolingo (reference for streak design; daily)**
- The streak freeze exists because a lost streak "can feel demotivating". Duolingo cites Penn/UCLA research that some slack motivates more than rigid rules. — [Duolingo blog: how the streak builds habit](https://blog.duolingo.com/how-duolingo-streak-builds-habit)
- In Duolingo's own experiments, letting learners equip two freezes at once raised daily active learners by about 0.38%. Separating the streak from the daily XP goal raised Day-14 retention by 3.3%. Learners on a 7-day streak are 2.4 times more likely to return the next day. A "weekend amulet" protecting weekends lifted Day-7 retention (reported +14%). These are company A/B results, not peer-reviewed. — [Duolingo blog: improving the streak](https://blog.duolingo.com/improving-the-streak); [Duolingo blog: streak research](https://blog.duolingo.com/duolingo-streak-research/); [Lenny's Newsletter](https://www.lennysnewsletter.com/p/duolingo-streaks)

**Habitica (habit RPG; daily; explicit HP decay)**
- Incomplete Dailies damage the player's HP. Running out of HP means "death" and losing a level, gold and an item. "Rest in the Inn" (or "Pause Damage" in settings) stops that damage during illness or vacation. It must be active when the daily cron runs. In boss quests the player still takes damage from party members' missed Dailies. — [Habitica Wiki: Rest in the Inn](https://habitica.fandom.com/wiki/Rest_in_the_Inn); [Habitica Wiki: Death](https://habitica.fandom.com/wiki/Death)

**StepN (move-to-earn; daily energy-limited earning; financial loss)**
- GST (the in-game earning token) fell about 98–99% from its all-time high within roughly two months of spring 2022. GMT (the governance token) fell over 80%. Mainland China users were cut off in May 2022, and GMT dropped on the news. — [CryptoSlate](https://cryptoslate.com/is-move-to-earn-dead-stepns-gst-token-tumbles-98-in-2-months/); [BeInCrypto](https://beincrypto.com/?p=234806); [FXStreet, 27 May 2022](https://fxstreet.com/cryptocurrencies/news/stepns-gmt-price-crashes-after-china-ban-showing-serious-red-flags-202205271435)
- Monthly active users peaked at about 705k in May 2022 and fell about 95% (Dune data). In October 2022 there were about 99k MAU and 5.8k DAU (Motley Fool). By April 2023 MAU was under 5k (Invezz). The counting methods differ. — [AMBCrypto](https://ambcrypto.com/stepn-users-take-a-step-back-from-running-after-airdrop-misery/); [Motley Fool, 20 Oct 2022](https://www.fool.com/investing/2022/10/20/should-investors-be-worried-about-stepn); [Invezz, 30 Apr 2023](https://invezz.com/news/2023/04/30/sweat-and-gmt-prices-struggle-as-move-to-earn-active-users-fall/)
- Analysts described a play-to-earn style "death spiral": earnings depend on new buyers of shoe NFTs. — [Motley Fool](https://www.fool.com/investing/2022/10/20/should-investors-be-worried-about-stepn)

**Zombies, Run! (story missions per run; no daily goal)**
- It is a narrative audio game that unlocks missions by running. It was tested against a non-immersive app and a control in the AIMFIT RCT (see Q2). — [JMIR 2015 AIMFIT](https://www.jmir.org/2015/8/e210/)

### Inferences
- The common pattern across vendors since 2018 is to **anchor a weekly minutes budget to the 150-minute guideline**, with intensity weighted (vigorous counts 2x in Fitbit and Google). Apple is the outlier, keeping daily calorie-based rings, but it added per-weekday goals.
- Every system that had a **hard daily failure state** (Apple streaks, Duolingo streaks, Habitica HP) later added a **user-initiated pause or protection**. Wander World's rest mode pausing cooling fits that convergent pattern.
- The games (Pokémon GO, Pikmin Bloom) **never take away what was earned**. Missing a goal only forfeits an extra bonus. None of the reviewed apps decays the player's accumulated world or collection for inactivity. Wander World's weekly cooling is therefore unusual and closest to Habitica's HP model, though softer.
- Fitbit's 2023 removal of challenges also wiped earned trophies, and users responded with strong protests. This is evidence that **losing earned symbols feels punitive**, even when the loss is not tied to performance.

### Gaps
- **Garmin's auto-goal formula** could not be confirmed from an official source in this pass. It is widely described in user communities as raising the goal after it is met and lowering it after a miss, but this was not verified, so do not cite it as fact.
- I found no current (2025–2026) official reward table for Pokémon GO Adventure Sync and no current Google Fit documentation.
- I found no official sources this pass for **Ring Fit Adventure** (Nintendo Switch, 2019: daily "workout" sessions, exertion level, no hard streak penalty in my recollection) or **Walkr** (steps fuel a spaceship). Both are unverified here.
- Apple's 90-day pause cap is a single-source claim.

## Q2. Published evidence on activity change, retention and decline over time

### Takeaway
Location games produce **real but short-lived step increases**. Howe et al. (BMJ 2016) found +955 steps a day in week 1, gone by week 6. Althoff et al. (JMIR 2016) found about +1,473 steps a day (+25%) in highly engaged players over about 30 days. RCT meta-analysis of gamified interventions shows a small-to-medium effect: Mazeas 2022 found g=0.42 and about +1,600 steps a day. Effects mostly fade when the game stops, except in the competition arm of STEP UP. Vendor research (Apple Heart and Movement Study) is correlational marketing summary, not causal evidence.

### Cited Findings
- **Howe et al., BMJ 2016 (Christmas issue, 13 Dec 2016):** a difference-in-differences study of 18–35-year-old iPhone users, comparing step data 4 weeks before and 6 weeks after installation. Players gained **955 extra steps a day in week 1**, and the effect declined to **baseline by week 6**. The authors call the effect moderate. Limitation: the phone counts steps only when carried. The player count is reported as 560, with sources differing (622 in STAT). — [BMJ abstract via JECH link](https://jech.bmj.com/lookup/ijlink/YTozOntzOjQ6InBhdGgiO3M6MTQ6Ii9sb29rdXAvaWpsaW5rIjtzOjU6InF1ZXJ5IjthOjQ6e3M6ODoibGlua1R5cGUiO3M6NDoiQUJTVCI7czoxMToiam91cm5hbENvZGUiO3M6MzoiYm1qIjtzOjU6InJlc2lkIjtzOjE4OiIzNTUvZGVjMTNfMTUvaTYyNzAiO3M6NDoiYXRvbSI7czo0NDoiL2plY2gvZWFybHkvMjAxOC8wMS8xMi9qZWNoLTIwMTctMjA5OTQzLmF0b20iO31zOjg6ImZyYWdtZW50IjtzOjA6IiI7fQ==); [Karolinska news](https://news.ki.se/pokemon-go-only-slightly-improves-physical-activity-among-adults); [MedicalXpress](https://medicalxpress.com/news/2016-12-pokemon-moderately-physical-adults-effect.html); [STAT](https://www.statnews.com/2016/12/13/pokemon-go/)
- **Althoff, White & Horvitz, JMIR 2016;18(12):e315:** used search logs plus Microsoft Band data from about 32,000 users over 3 months. The most engaged players added **about 1,473 steps a day (more than 25%)** over the 30 days after starting (P<.001). The authors estimate the game added 144 billion steps to US activity. They also report reaching users with low prior activity (full text not fetched). The authors say long-term effects need study. — [JMIR](https://www.jmir.org/2016/12/e315); [Microsoft Research](https://www.microsoft.com/en-us/research/publication/influence-pokemon-go-physical-activity-study-implications/); [author PDF](https://homes.cs.washington.edu/~althoff/docs/althoff_pokemon_go_physical_activity_jmir.pdf)
- **Mazeas et al., JMIR 2022;24(1):e26779:** a PROSPERO-registered meta-analysis of RCTs (16 studies, 2,407 participants). Gamification had a small-to-medium effect on physical activity, **g=0.42 (95% CI 0.14–0.69)**. Steps: **+1,609.56 a day (95% CI 372–2,847)**. MVPA: g=0.31 with a CI crossing zero. A later letter flagged heterogeneity and limited applicability to older adults. — [JMIR](https://jmir.org/2022/1/e26779)
- **Patel et al., STEP UP, JAMA Internal Medicine 2019:** 602 adults with BMI ≥25, a 24-week intervention plus 12-week follow-up (Feb 2018–Mar 2019). The game used **loss aversion**: 70 points at the start of each week, minus 10 for each day the step goal was missed, and levels that moved up or down weekly. All gamified arms beat control during the intervention. Only the **competition** arm stayed above control after the game ended (+569 steps a day). — [PubMed 31498375](https://pubmed.ncbi.nlm.nih.gov/31498375/); [Penn Medicine release, Sep 2019](https://pennmedicine.org/news/news-releases/2019/september/using-a-wearable-device-to-exercise-more-add-competition-to-improve-your-results); [ACC summary](https://www.acc.org/latest-in-cardiology/articles/2019/11/26/08/23/key-takeaways-regarding-gamification-to-increase-physical-activity)
- **Zombies, Run! (AIMFIT RCT, JMIR 2015):** a 3-arm RCT of 51 New Zealand adolescents aged 14–17 (immersive Zombies, Run! vs non-immersive Get Running vs control). I could not confirm the outcomes from snippets. My recollection is no significant difference in fitness, but that is not verified. Smaller studies (n=20 crossover, n=10 feasibility) report acceptability and motivation. — [JMIR 2015;17(8):e210](https://www.jmir.org/2015/8/e210/); [UCL interviews](https://discovery-pp.ucl.ac.uk/10139156/3/Potts_ZR_users_interviews_paper_Games%20for%20Health_Last%20version_NF.pdf)
- **Pikmin Bloom:** I found no peer-reviewed outcome study. A Taipei Medical University trial (NCT06910995) is testing it for outdoor walking habits in middle-aged and older adults and is still recruiting. — [trial record](https://www.syfrah.com/trial/NCT06910995)
- **Apple Heart and Movement Study (vendor summaries):** over 140,000 participants. Those who closed all three rings at least 50% of the time, versus 10% or less, were 48% less likely to report poor sleep quality and 57% less likely to report elevated stress (April 2025). In a January 2026 release, nearly 80% of people who raised exercise minutes in early January maintained them through late January. Both are correlational, company-published and lightly described methodologically. — [Apple Newsroom, Apr 2025](https://www.apple.com/newsroom/2025/04/get-active-with-apple-watch/); [Apple Newsroom, Jan 2026](https://www.apple.com/mt/newsroom/2026/01/apple-watch-keeps-users-active-and-motivated-in-2026/)
- **StepN:** no health-outcome evidence was found. Engagement collapsed about 95% within months, in step with token prices (see Q1).

### Inferences
- Novelty effects decay over weeks (Howe: gone by week 6). Systems that want **sustained** change need a mechanism that survives novelty: social competition (STEP UP), habit, or a renewing goal. A one-off reward schedule does not do it.
- STEP UP is the strongest RCT evidence that **loss-framed weekly points** raise activity while running. It is the closest published analogue to Wander World's weekly cooling. The effect mostly vanished when the mechanic stopped, which argues for a long-running loop rather than a time-boxed one.
- The meta-analytic effect (about +1,600 steps a day) is clinically meaningful but heterogeneous. It should not be presented to the player as a promise.

### Gaps
- I could not read the Althoff full text (PMC blocked) to confirm its decay curve and subgroup numbers.
- I found no peer-reviewed long-term retention data for Apple rings, Garmin auto goals or Fitbit AZM. Vendor figures only.
- I found no peer-reviewed study of Duolingo streak effects; company A/B posts only.

## Q3. Criticisms (compulsion, streak guilt, financialisation, ignoring rest) and vendor responses

### Takeaway
Criticism centres on **streak anxiety and exercising while ill** (Apple rings, before watchOS 11), **lower intrinsic enjoyment from quantification** (Etkin 2016), **losing earned items** (Fitbit 2023) and **financialised harm** (StepN). Vendors responded with pauses and freezes: Apple's pause rings and per-day goals (2024), Duolingo's freeze and weekend amulet, and Habitica's Inn and pause damage. Fitbit's response was readiness-aware targets (2021).

### Cited Findings
- Users describe anxiety about ending long streaks (one writer has a 2,000+ day Move streak), being more upset about a broken streak than about being ill, and pressure to close rings while sick or grieving. — [TechRadar](https://techradar.com/health-fitness/smartwatches/my-apple-watch-is-going-to-hate-this-our-wearables-are-making-us-anxious-and-obsessive-heres-what-we-can-all-do-about-it); [Ash Furrow, 2021](https://ashfurrow.com/blog/2021-my-year-of-closed-rings/); [Stuff](https://www.stuff.tv/?p=896057); [WhistleOut: "Apple Watch covid rest day please"](https://whistleout.com.au/MobilePhones/News/Apple-Watch-covid-rest-day-please)
- Fortune (January 2025) reports people abandoning Apple Watches after feeling "bullied" by ring reminders. It notes Apple now offers pause and per-weekday goals. — [Fortune, 24 Jan 2025](https://fortune.com/well/2025/01/24/apple-watch-bullied-burn-calories-close-rings-obsession-fitness-trackers-notifications/)
- Counterpoint: one reviewer found that breaking their streak led to more ambitious, better training. — [Tom's Guide](https://www.tomsguide.com/wellness/smartwatches/breaking-your-apple-watch-move-streak-could-help-you-get-fitter-heres-how)
- **Etkin 2016 (Journal of Consumer Research 42(6):967–984):** across six experiments, tracking increased output but reduced enjoyment. In a walking study (about 95 participants), pedometer users walked more but enjoyed it less, found it more like work, and were less satisfied at the end of the day. The effect disappeared when the activity was framed by its intrinsic value (learning). Etkin warns this may reduce future activity. — [Wharton working paper PDF](https://marketing.wharton.upenn.edu/wp-content/uploads/2016/10/Etkin-Jordan-11-12-15-Hidden-Cost.pdf); [Duke Fuqua](https://www.fuqua.duke.edu/duke-fuqua-insights/etkin-counting-steps)
- Duolingo openly says rigid streaks can backfire. The freeze and weekend protection were introduced to add "slack". — [Duolingo blog](https://blog.duolingo.com/how-duolingo-streak-builds-habit)
- **StepN:** real-money losses when tokens collapsed (GST about −98%). Earnings depended on buying NFT sneakers, and analysts flagged a Ponzi-like "death spiral". — [CryptoSlate](https://cryptoslate.com/is-move-to-earn-dead-stepns-gst-token-tumbles-98-in-2-months/); [Motley Fool](https://www.fool.com/investing/2022/10/20/should-investors-be-worried-about-stepn)
- **Fitbit's 2023 feature removal** wiped earned trophies and provoked long forum protests (49+ pages). — [Fitbit Community](https://community.fitbit.com/t5/Product-Feedback/Don-t-remove-Challenges-amp-Adventures/idi-p/5335785/page/49); [TechRadar](https://techradar.com/news/fitbit-owners-are-furious-as-google-axes-even-more-key-features)
- A Habitica design caveat: the Inn protects you, but party members' misses still damage you during boss quests, so social coupling can override rest. — [Habitica Wiki](https://habitica.fandom.com/wiki/Rest_in_the_Inn)

### Inferences
- The repeated vendor fix is a **player-controlled pause that preserves the streak or world**, available in advance and for illness. Wander World's rest mode matches this. Apple's variant also lets a pause run open-ended or to the end of a week or month, which avoids the player having to remember to un-pause each day.
- **Per-weekday or weekly goals** (Apple watchOS 11; Fitbit, Garmin and Google weekly minutes) reduce the daily all-or-nothing failure that drives streak guilt. A weekly ledger like Wander World's is already on the "healthier" side of this split.
- Etkin's moderator (framing by intrinsic value) suggests surfacing **discovery and exploration** rather than raw minutes. That matches the player's stated preference for "discovery over routine".
- Avoid anything that converts activity into tradeable value (the StepN lesson). Wander World's non-competitive, offline design already does.

### Gaps
- I found no peer-reviewed study measuring guilt or compulsion from Apple ring streaks specifically. The evidence is journalistic and first-person.
- I found no published data on uptake of Apple's pause rings, or on whether pausing changes activity afterwards.

## Q4. Which designs researchers and reviewers see as healthy vs harmful

### Takeaway
"Healthy" in the sources means: **guideline-anchored weekly minute goals, adaptive or personalised targets, explicit rest and illness handling, slack in streaks, intrinsic framing, and no loss of earned progress**. "Harmful" means: **rigid daily all-or-nothing streaks, punishing illness, losing earned items, and financialised rewards**. Loss-framed points are effective in an RCT (STEP UP). That makes loss framing a double-edged tool rather than simply harmful.

### Cited Findings
- **Weekly guideline-based minutes** are the design chosen by AHA/WHO-partnered Google Heart Points (2018) and Fitbit AZM. — [AHA](https://www.heart.org/en/news/2018/08/21/google-just-launched-heart-points-here-are-5-things-you-need-to-know); [Google Store](https://store.google.com/magazine/fitbit_active_zone_minutes?hl=en-US)
- **Readiness-adjusted targets** (Fitbit) prescribe recovery instead of failure on low-readiness days. — [Google/Fitbit help](https://support.google.com/fitbit/answer/14236710)
- **Slack beats rigidity**, the rationale Duolingo gives, citing Penn/UCLA research. Duolingo's A/B tests show retention gains from freezes and weekend protection. — [Duolingo blog](https://blog.duolingo.com/how-duolingo-streak-builds-habit); [Duolingo blog: improving the streak](https://blog.duolingo.com/improving-the-streak)
- **Loss aversion works while active:** in STEP UP, weekly points lost for missed days increased steps versus control, but the gains largely did not persist after removal, except with competition. — [PubMed 31498375](https://pubmed.ncbi.nlm.nih.gov/31498375/)
- **Quantification can crowd out enjoyment** (Etkin 2016). — [Duke Fuqua](https://www.fuqua.duke.edu/duke-fuqua-insights/etkin-counting-steps)
- Reviewers and journalists praised Apple's 2024 rest days as overdue and humane. Journalists criticised streak pressure during illness. — [iMore](https://www.imore.com/health-fitness/apple-watch/after-9-years-apple-has-finally-added-the-apple-watch-feature-weve-been-begging-for-rest-days-are-here-and-they-wont-break-your-award-streaks); [BGR](https://bgr.com/tech/with-watchos-11-pausing-my-activity-rings-wont-make-me-feel-bad-about-myself-anymore/); [Fortune](https://fortune.com/well/2025/01/24/apple-watch-bullied-burn-calories-close-rings-obsession-fitness-trackers-notifications/)
- The **financialised** move-to-earn model collapsed within months, taking user funds and engagement with it. — [CryptoSlate](https://cryptoslate.com/is-move-to-earn-dead-stepns-gst-token-tumbles-98-in-2-months/); [AMBCrypto](https://ambcrypto.com/stepn-users-take-a-step-back-from-running-after-airdrop-misery/)

### Inferences
- Wander World's design (a weekly minutes goal with intensity weighting, a partial ¼-step cool on a bad week, a floor two steps below best, recovery on a good week, a rest pause and gold never reduced) lines up with the "healthy" cluster on almost every axis.
  - The main tension is that cooling makes **visible earned progress decay**. No mainstream app reviewed does that except Habitica, and Fitbit's trophy removal shows users strongly resent losing earned symbols.
  - Mitigations already present (a floor, a lag before a step shows as lost, recovery, rest mode) mirror the "slack" pattern.
  - Framing cooling as seasonal or natural rather than as a penalty would follow Etkin's intrinsic-framing finding. This is an inference, not tested.
- A Garmin- or Fitbit-style **adaptive goal based on recent baseline** is common in industry, but it is not well documented or evaluated in public. If Wander World adapts its goal, it should publish its own rule to the player, which none of the vendors do clearly.

### Gaps
- I found no systematic review that directly compares weekly and daily goals, or adaptive and fixed goals, on long-term outcomes in consumer apps. Evidence is from single trials and vendor material.
- I found no researcher commentary specifically on "decaying world" mechanics in exergames.
