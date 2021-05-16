## Traderimo Test Task

You may read task conditions in task_description.pdf.

There are two implementations:

- PriceThrottler. It's based on java core and provides events which has come only after subscription.
- SnapshotPriceThrottler. It's based on kotlin and coroutines and provides: (1) last cached events for new subscribers
  and (2) events which will come after subscription.
  
Project has tests. See corresponding folder.
Also, there is 'Repeat-tests-until-fail' idea run (saved configuration file). 