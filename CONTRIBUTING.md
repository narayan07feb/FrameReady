# Contributing to FrameReady

First off, thank you for considering contributing to `FrameReady`! It's people like you that make open source such a great community.

## 1. Where do I go from here?

If you've noticed a bug or have a feature request, make one! It's generally best if you get confirmation of your bug or approval for your feature request this way before starting to code.

## 2. Fork & create a branch

If this is something you think you can fix, then fork `FrameReady` and create a branch with a descriptive name.

A good branch name would be (where issue #325 is the ticket you're working on):
```sh
git checkout -b 325-fix-concurrent-modification
```

## 3. Local Development

1. Open the project in Android Studio.
2. Ensure you can build the `:frameready` module locally.
3. The easiest way to test your changes is to modify the `:sample-standard` module to execute your new logic and verify it works on an emulator.

## 4. Testing

`FrameReady` is a performance-critical startup library. If you are modifying the core dispatching algorithms or Kahn's topological sort mechanisms, please ensure that:
- The library still builds with `assembleRelease`
- The `:benchmark` Macrobenchmark tests still pass successfully without regressions in TTFF!

## 5. Submit a Pull Request

When you're finished, push to your fork and submit a pull request.
Make sure to fill out the provided Pull Request template, linking it to the relevant issue!
