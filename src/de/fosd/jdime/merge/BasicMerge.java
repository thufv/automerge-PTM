package de.fosd.jdime.merge;

import de.fosd.jdime.artifact.Artifact;
import de.fosd.jdime.config.merge.MergeContext;
import de.fosd.jdime.config.merge.MergeScenario;
import de.fosd.jdime.config.merge.MergeType;
import de.fosd.jdime.config.merge.Revision;
import de.fosd.jdime.matcher.matching.Matching;
import de.fosd.jdime.operations.AddOperation;
import de.fosd.jdime.operations.ConflictOperation;
import de.fosd.jdime.operations.DeleteOperation;
import de.fosd.jdime.operations.MergeOperation;

import java.util.Optional;
import java.util.logging.Logger;

import static de.fosd.jdime.artifact.Artifacts.copyTree;
import static de.fosd.jdime.config.merge.MergeScenario.BASE;

public class BasicMerge<T extends Artifact<T>> {

    private static final Logger LOG = Logger.getLogger(BasicMerge.class.getCanonicalName());

    /**
     * Applying a simple merge when `node` has no proper match in the `other` revision.
     * <p>
     * Cases:
     * 1) If `node` is in both self revision and base revision,
     * 1-1) when `node` has changed from base revision, then this is a insertion-deletion-conflict.
     * 1-2) when `node` is consistent with base revision, then delete it from target.
     * 2) If `node` is only in self revision and has no matching in base revision,
     * then add the change to target.
     *
     * @param node    node where merging is performed.
     * @param target  merge target.
     * @param context merge context.
     * @param self    revision of node.
     * @param other   revision of other (which is empty).
     * @param b       revision of base.
     * @param isLeft  if the revision of `self` is left.
     */
    protected void simpleMerge(T node, T target, MergeContext context,
                               Revision self, Revision other, Revision b, boolean isLeft) {
        Optional<T> base = node.getProperMatch(b, context);

        if (base.isPresent()) { // 1) `pivot` in BL
            LOG.fine(() -> String.format("Simple: %s was deleted by %s", show(node), other.getName()));

            if (node.hasChanges(b)) {
                // 1-1) insertion-deletion-conflict
                LOG.fine(() -> String.format("Simple: %s has changed base.", show(node)));

                ConflictOperation<T> conflictOp = new ConflictOperation<>(
                        node, null, target, self.getName(), other.getName(), base.get(), isLeft);
                conflictOp.apply(context);
            } else {
                // 1-2) can be safely deleted
                DeleteOperation<T> delOp = new DeleteOperation<>(node, target, self.getName());
                delOp.apply(context);
            }
        } else { // 2) `pivot` only in L
            // add the change
            LOG.fine(() -> String.format("Simple: %s is a change", show(node)));

            AddOperation<T> addOp = new AddOperation<>(copyTree(node), target, self.getName());
            node.setMerged();
            addOp.apply(context);
        }
    }

    /**
     * Perform a 2 or 3-way merge.
     *
     * @param left    left artifact.
     * @param right   right artifact.
     * @param target  target artifact.
     * @param base    base revision.
     * @param context merge context.
     */
    protected void twoOrThreeWayMerge(T left, T right, T target, Revision base, MergeContext context) {
        Matching<T> mBase = left.getMatching(base);

        // determine whether the child is 2 or 3-way merged
        MergeType childType = mBase == null ? MergeType.TWOWAY
                : MergeType.THREEWAY;
        T baseChild = mBase == null ? left.createEmptyArtifact(BASE)
                : mBase.getMatchingRevision(base);
        T targetChild = target == null ? null : left.copy();
        if (targetChild != null) {
            target.addChild(targetChild);

            assert targetChild.exists();
            targetChild.clearChildren();
        }

        MergeScenario<T> childTriple = new MergeScenario<>(childType,
                left, baseChild, right);
        MergeOperation<T> mergeOp = new MergeOperation<>(childTriple, targetChild);

        left.setMerged();
        right.setMerged();
        mergeOp.apply(context);
    }

    /**
     * Returns the logging show.
     *
     * @param artifact artifact that is subject of the logging
     * @return logging show
     */
    protected String show(T artifact) {
        return String.format("(%s) %s", (artifact == null) ? "null" : artifact.getId(), artifact);
    }
}
