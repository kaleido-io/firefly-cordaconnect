Test application to reproduce liveness issue observed in corda

## PRE-REQS
- JDK 1.8 (such as from [https://adoptopenjdk.net/](https://gradle.org/install/))
- gradle ([https://gradle.org/install/](https://gradle.org/install/))

## BUILD
```
./gradlew build
```

## Run test

```
./gradlew :workflows:integrationTest -DintegrationTest.concurrency=8 -DintegrationTest.numberOfTransactions=1000 --info
```