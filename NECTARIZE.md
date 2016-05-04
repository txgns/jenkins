# This is a CloudBees managed LTS versions of Jenkins core
The presence of this file indicates that this Jenkins is turned into Nectar.

When we take OSS Jenkins and start our sustaining branches (aka Nectarize), a few changes need
to be made to POM. The `nectarize` branch keeps track of the changes we need. The idea
is whenever we need to nectarize, we do so by merging this branch into it.

## How to nectarize
When the community is done with its LTS releases on a `stable-1.xxx` branch and CloudBees is
ready to take over, we do the following:

* In this process, we refer to `http://github.com/jenkinsci/jenkins` as the `community` repo,
  and `http://github.com/cloudbees/hudson` as the `cloudbees` repo.

* Determine the correct 3rd digit for the nectarized version, such as `1.xxx.18`.
  This 3rd digit is kept the same across all the sustaining LTS branches that are
  actively maintained, so the easiest way is to find this is to check out the previous
  nectarized LTS line from the cloudbees repo and look at its version number.
  We will refer to this number in `1.xxx.yy` as `yy`

* Checkout the `stable-1.xxx` branch from the community repo,
  which should have `1.xxx.3-SNAPSHOT` as its version number

* Run `mvn release:update-versions -DdevelopmentVersion=1.xxx.yy-SNAPSHOT` on
  the workspace from the previous step. Manually make similar edits in `plugins/pom.xml`
  If unsure, see commit d08cf489bce4ab8f109c59b1ce3aaec7a87d3298 for an actual example of
  how this was done. This step reduces the merge conflict in the next step.

* Run `git merge nectarize` to merge the tip of the `nectarize` branch from the cloudbees repo.
  If this step results in merge conflicts, *DO NOT RESOLVE merge conflicts here*. Instead,
  abandon the merge, follow the "Update nectarize branch" process below, then retry this
  step from scratch.

* Push the resulting `stable-1.xxx` into the cloudbees repo. Congratulations,
  a new LTS release line is properly nectarized.

## How to update nectarize branch
The `nectarize` branch contains POM changes that are necessary to internalize LTS release lines,
and normally it should merge cleanly to any `stable-1.xxx` branches. However, on rare occasions,
the OSS Jenkins project modifies POMs in ways that cause merge conflicts.

When this happens, it is important to update the `nectarize` branch to reflect this changes,
instead of resolving merge conflicts at the point of `stable-1.xxx` merges. Otherwise there
won't be any guarantee that such merge conflicts are resolved consistently.

Say you discovered that you need to update the `nectarize` branch while trying to nectarize
`stable-1.zzz`. The following process updates `nectarize` branch:

* Checkout the `nectarize` branch from the cloudbees repo
* `git merge 'jenkins-1.zzz^' to merge the parent commit of the release tag into this workspace.
* Resolve merge conflicts carefully. If necessary, check the difference between the `nectarize`
  branch and its previous base.
* Commit the change and push that into the `nectarize` branch

