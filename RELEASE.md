# Zipkin Release Process

This repo uses semantic versions. Please keep this in mind when choosing version numbers.

1. **Alert others you are releasing**

   There should be no commits made to master while the release is in progress (about 10 minutes). Before you start
   a release, alert others on [gitter](https://gitter.im/openzipkin/zipkin) so that they don't accidentally merge
   anything. If they do, and the build fails because of that, you'll have to recreate the release tag described below.

1. **Push a git tag**

   The tag should be of the format `release-N.M.L`, for example `release-3.7.1`.

1. **Wait for Travis CI**

   This part is controlled by [`travis/publish.sh`](travis/publish.sh). It creates a bunch of new commits, bumps
   the version, publishes artifacts and syncs to Maven Central.

## Credentials

Credentials of various kind are needed for the release process to work. If you notice something
failing due to unauthorized, re-encrypt them using instructions at the bottom of the `.travis.yml`

Ex You'll see comments like this:
```yaml
env:
  global:
  # Ex. travis encrypt BINTRAY_USER=your_github_account
  - secure: "VeTO...
```

To re-encrypt, you literally run the commands with relevant values and replace the "secure" key with the output:

```bash
$ travis encrypt BINTRAY_USER=adrianmole
Please add the following to your .travis.yml file:

  secure: "mQnECL+dXc5l9wCYl/wUz+AaYFGt/1G31NAZcTLf2RbhKo8mUenc4hZNjHCEv+4ZvfYLd/NoTNMhTCxmtBMz1q4CahPKLWCZLoRD1ExeXwRymJPIhxZUPzx9yHPHc5dmgrSYOCJLJKJmHiOl9/bJi123456="
```

### Troubleshooting invalid credentials

If you receive a '401 unauthorized' failure from jCenter or Bintray, it is
likely `BINTRAY_USER` or `BINTRAY_KEY` entries are invalid, or possibly the user
associated with them does not have rights to upload.

The least destructive test is to try to publish a snapshot manually. By passing
the values Travis would use, you can kick off a snapshot from your laptop. This
is a good way to validate that your unencrypted credentials are authorized.

Here's an example of a snapshot deploy with specified credentials.
```bash
$ BINTRAY_USER=adrianmole BINTRAY_KEY=ed6f20bde9123bbb2312b221 TRAVIS_PULL_REQUEST=false TRAVIS_TAG= TRAVIS_BRANCH=master travis/publish.sh
```

## First release of the year

The license plugin verifies license headers of files include a copyright notice indicating the years a file was affected.
This information is taken from git history. There's a once-a-year problem with files that include version numbers (pom.xml).
When a release tag is made, it increments version numbers, then commits them to git. On the first release of the year,
further commands will fail due to the version increments invalidating the copyright statement. The way to sort this out is
the following:

Before you do the first release of the year, move the SNAPSHOT version back and forth from whatever the current is.
In-between, re-apply the licenses.
```bash
$ ./mvnw versions:set -DnewVersion=1.3.3-SNAPSHOT -DgenerateBackupPoms=false
$ ./mvnw com.mycila:license-maven-plugin:format
$ ./mvnw versions:set -DnewVersion=1.3.2-SNAPSHOT -DgenerateBackupPoms=false
$ git commit -am"Adjusts copyright headers for this year"
```

## Manually releasing

If for some reason, you lost access to CI or otherwise cannot get automation to work, bear in mind this is a normal maven project, and can be released accordingly. The main thing to understand is that libraries are not GPG signed here (it happens at bintray), and also that there is a utility to synchronise to maven central. Note that if for some reason [bintray is down](https://status.bintray.com/), the below will not work.

```bash
# First, set variable according to your personal credentials. These would normally be decrypted from .travis.yml
BINTRAY_USER=your_github_account
BINTRAY_KEY=xxx-https://bintray.com/profile/edit-xxx
SONATYPE_USER=your_sonatype_account
SONATYPE_PASSWORD=your_sonatype_password
VERSION=xx-version-to-release-xx

# now from latest master, prepare the release. We are intentionally deferring pushing commits
./mvnw --batch-mode -s ./.settings.xml -Prelease -nsu -DreleaseVersion=$VERSION -Darguments="-DskipTests -Dlicense.skip=true" release:prepare  -DpushChanges=false

# once this works, deploy and synchronize to maven central
git checkout $VERSION
./mvnw --batch-mode -s ./.settings.xml -Prelease -nsu -DskipTests deploy
./mvnw --batch-mode -s ./.settings.xml -nsu -N io.zipkin.centralsync-maven-plugin:centralsync-maven-plugin:sync

# if all the above worked, clean up stuff and push the local changes.
./mvnw release:clean
git checkout master
git push
git push --tags
```
