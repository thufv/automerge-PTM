package de.fosd.jdime;

import de.fosd.jdime.artifact.ast.ASTNodeArtifact;
import de.fosd.jdime.artifact.file.FileArtifact;
import de.fosd.jdime.config.merge.MergeContext;
import de.fosd.jdime.config.merge.MergeScenario;
import de.fosd.jdime.matcher.Matcher;
import de.fosd.jdime.matcher.matching.Color;
import de.fosd.jdime.matcher.matching.Matching;
import org.apache.commons.math3.util.Pair;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static de.fosd.jdime.artifact.file.FileArtifact.*;
import static de.fosd.jdime.util.SuccessLevel.SUCCESS;

public class Checker {

    private static final Logger LOG = Logger.getLogger(Checker.class.getCanonicalName());

    public static String TMP_FOLDER = "/tmp";

    public static Pair<Boolean, Pair<Double, Integer>> check(FileArtifact expected, FileArtifact target,
                                                             boolean hasConflict) {
        MergeContext context = new MergeContext();
        LOG.info("Expected: " + expected.getFile().getAbsolutePath());
        ASTNodeArtifact exp = expected.createASTNodeArtifact(MergeContext.EXPECTED);

        if (hasConflict) {
            // get resolution using left/right
            String[] lines = target.getContent().split("\\r?\\n");

            List<String> leftLines = new ArrayList<>();
            List<String> rightLines = new ArrayList<>();
            boolean leftOn = true;
            boolean rightOn = true;

            for (String line : lines) {
                if (line.startsWith(CONFLICT_START)) {
                    rightOn = false;
                } else if (line.startsWith(CONFLICT_DELIM)) {
                    leftOn = false;
                    rightOn = true;
                } else if (line.startsWith(CONFLICT_END)) {
                    leftOn = true;
                } else {
                    if (leftOn) {
                        leftLines.add(line);
                    }
                    if (rightOn) {
                        rightLines.add(line);
                    }
                }
            }

            // save temp files
            Path leftPath = Paths.get(TMP_FOLDER, "AutoMerge.Tmp.LeftTarget.java");
            File leftFile = leftPath.toFile();
            PrintWriter pw = null;
            try {
                pw = new PrintWriter(new FileWriter(leftFile));
            } catch (IOException e) {
                e.printStackTrace();
            }
            for (String line : leftLines) {
                pw.println(line);
            }
            pw.close();

            Path rightPath = Paths.get(TMP_FOLDER, "AutoMerge.Tmp.RightTarget.java");
            File rightFile = rightPath.toFile();
            try {
                pw = new PrintWriter(new FileWriter(rightFile));
            } catch (IOException e) {
                e.printStackTrace();
            }
            for (String line : rightLines) {
                pw.println(line);
            }
            pw.close();

            // create ASTs
            ASTNodeArtifact left = new FileArtifact(MergeScenario.TARGET, leftFile)
                    .createASTNodeArtifact(MergeScenario.TARGET);
            ASTNodeArtifact right = new FileArtifact(MergeScenario.TARGET, rightFile)
                    .createASTNodeArtifact(MergeScenario.TARGET);

            // diff exp left
            Matcher<ASTNodeArtifact> matcher1 = new Matcher<>(exp, left);
            Matching<ASTNodeArtifact> m1 = matcher1.match(context, Color.BLUE).get(exp, left).get();
            int size1 = exp.getTreeSize() + left.getTreeSize();
            int unMatched1 = size1 - 2 * m1.getScore();
            double unMatchedRate1 = (double) unMatched1 / size1;

            // diff exp right
            Matcher<ASTNodeArtifact> matcher2 = new Matcher<>(exp, right);
            Matching<ASTNodeArtifact> m2 = matcher2.match(context, Color.BLUE).get(exp, right).get();
            int size2 = exp.getTreeSize() + right.getTreeSize();
            int unMatched2 = size2 - 2 * m2.getScore();
            double unMatchedRate2 = (double) unMatched2 / size2;

            LOG.fine(String.format("Check: left matcher = %f (%d/%d), right matcher = %f (%d/%d)",
                    unMatchedRate1, unMatched1, exp.getTreeSize(), unMatchedRate2, unMatched2, exp.getTreeSize()));
            return Pair.create(
                    false,
                    unMatchedRate1 > unMatchedRate2 ? Pair.create(unMatchedRate1, unMatched1) :
                            Pair.create(unMatchedRate2, unMatched2)
            );
        }

        ASTNodeArtifact tar = target.createASTNodeArtifact(MergeScenario.TARGET);

        // diff exp tar
        Matcher<ASTNodeArtifact> matcher = new Matcher<>(tar, exp);
        Matching<ASTNodeArtifact> m = matcher.match(context, Color.BLUE).get(tar, exp).get();
        int size = exp.getTreeSize() + tar.getTreeSize();
        int unMatched = size - 2 * m.getScore();
        double unMatchedRate = (double) unMatched / size;
        return Pair.create(m.hasFullyMatched(),
                Pair.create(unMatchedRate, unMatched));
    }

    public static void applyCheck(String expected, String target) {
        FileArtifact exp = new FileArtifact(MergeContext.EXPECTED, new File(expected));
        FileArtifact tar = new FileArtifact(MergeScenario.TARGET, new File(target));
        boolean hasConflict = tar.getContent().contains(CONFLICT_START);

        if (hasConflict) {
            LOG.warning("Check: Output has conflict: " + tar.getFile().getAbsolutePath());
        }

        showCheckResult(check(exp, tar, hasConflict));
    }

    public static void showCheckResult(Pair<Boolean, Pair<Double, Integer>> p) {
        if (p.getFirst()) { // fully matched
            LOG.log(SUCCESS, "Check: FULLY MATCHED");
        } else {
            LOG.severe(String.format("Check: NOT MATCHED: %f (%d unmatched)",
                    p.getSecond().getFirst(), p.getSecond().getSecond()));
        }
    }

    public static Pair<Boolean, Pair<Double, Integer>> check(ASTNodeArtifact tar, ASTNodeArtifact exp) {
        MergeContext context = new MergeContext();

        // diff ast1 ast2
        Matcher<ASTNodeArtifact> matcher = new Matcher<>(tar, exp);
        Matching<ASTNodeArtifact> m = matcher.match(context, Color.BLUE).get(tar, exp).get();
        int size = exp.getTreeSize() + tar.getTreeSize();
        int unMatched = size - 2 * m.getScore();
        double unMatchedRate = (double) unMatched / size;
        return Pair.create(m.hasFullyMatched(),
                Pair.create(unMatchedRate, unMatched));
    }

    public static boolean astEqual(ASTNodeArtifact ast1, ASTNodeArtifact ast2) {
        return check(ast1, ast2).getFirst();
    }
}
