# Structured Merge with Proper Tree Matching

AutoMerge is a variant of [JDime](https://github.com/se-sic/jdime) on structured merging. This repository is a branch on improving merge precision using Proper Tree Matching (PTM). It is the prototype implementation of the paper *Enhancing Precision of Structured Merge by Proper Tree Matching* [(DOI)](https://ieeexplore.ieee.org/document/8802856).

This implementation is based on JDime v0.4.3.

## Build

Make sure your current shell is now running JDK 8 (even JDK 11 is too "new" for the gradle we are using).

### JNativeMerge (Dependency)

Since JDime uses [JNativeMerge](https://gitlab.infosun.fim.uni-passau.de/seibt/JNativeMerge) under the hood to implement line-based textual merging via the `libgit2` native library, you must have it installed and let JVM correctly load it. Otherwise, you are likely to meet the following exception:
```
java.lang.LinkageError: Failed to load the git2 native library.
  at de.uni_passau.fim.seibt.LibGit2.<clinit>(LibGit2.java:128)
```

According to [this issue](https://github.com/se-sic/jdime/issues/21), installing JNativeMerge with `libgit2` takes extra effort on Mac. Here, we summarize the installation steps that we succeeded in our Intel MacBook Pro (OS version 11.3):

1. Download `libgit2` [v0.28.3](https://github.com/libgit2/libgit2/releases/tag/v0.28.3)
2. Build `libgit2` via `cmake` (in folder `libgit2-0.28.3/`):
```sh
$ mkdir build && cd build
$ cmake ..
$ cmake --build .
```
3. The generated library file `libgit2-0.28.3/build/libgit2.dylib` is what we need
4. Clone [JNativeMerge](https://gitlab.infosun.fim.uni-passau.de/seibt/JNativeMerge) (under exactly this name) as a sibling folder (e.g. you have `Workspace/JNativeMerge/` and `Workspace/automerge-PTM/`)
5. In file `JNativeMerge/gradle.properties`, set `jnativemerge.libgit2.path=<Absolute Path to libgit2.dylib>`
6. In JNativeMerge project root folder `JNativeMerge/`, run `./gradlew test` to test if the library was correctly included
7. Switch back to this project root folder `automerge-PTM/`, create a file `gradle.properties` and write `JNM_MAVEN=false`

Now you are done. Go to next section "Gradle Build".

p.s. Try the above instructions if you meet difficulties on Linux or Windows (but you use a different dynamic library format).

### Gradle Build

In project root folder `automerge-PTM/`, run `./gradlew installDist`.

If you run this for the first time, it will take a couple of minutes to download the correct version of `gradle` and then the Java dependencies used by JDime.

If build is successful, run `./build/install/JDime/bin/JDime` (also in the project root folder). If successful, help messages are printed.

## Basic Usage

```sh
$ ./build/install/JDime/bin/JDime --mode structured --output <file/folder> <leftVersion> <baseVersion> <rightVersion>
```

This will perform a three-way structured merge on the merge scenario specified by the base, left and right version you provide. Only Java source files are considered and merged.

Our proper tree matching algorithm is for "structured" mode only. We provide an option `-th,--threshold <percentage>` to let users specify the "quality threshold ($\theta$)" (default value 0.5) mentioned in the paper. Other options are inherited from JDime.

## License

We are using the same license as in JDime: GNU Lesser General Public License. The license files are inherited from JDime repo.
