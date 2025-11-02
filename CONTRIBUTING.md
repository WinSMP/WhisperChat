# How to Contribute

## General workflow

0. Create a fork of the repository: <https://github.com/WinSMP/WhisperChat/fork>

1. Pull any changes from `main` to make sure you're up-to-date. If you don't do this, you may end up
    * implementing things that have already been merged/rejected;
    * causing conflicts with other contributors' changes

2. Create a branch from `main`
    * Give your branch a name that describes your change (e.g. add-scoreboard)
    * Focus on one change per branch

3. Commit and push your changes
    * Keep your commits atomic and focused
    * Write descriptive commit messages in [Conventional Commit](https://www.conventionalcommits.org/en/v1.0.0/) format.
    * Keep the commit header about 50 characters (but if it goes a little bit over, that's fine).
    * The commit body should be wrapped to 72 characters (tip: run `fold -s -w 72` on your commit message to wrap it if you're not sure).

4. When you're ready, create a pull request to `main`
   * Keep your PRs reasonably small (preferably <300 LOC)
   * The title should be descriptive but reasonably concise
   * List any changes made in your description
   * Link any issues that your pull request is related to as well

### Example

```text
feat: create scoreboard for total points

- Add scoreboard displayed in-game at game end
- Change `StorageManager` class to persist scoreboard data
```

After the pull request has been reviewed, approved, and passes all automated checks, it will be merged into `main`.
