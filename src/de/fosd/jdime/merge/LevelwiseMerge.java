package de.fosd.jdime.merge;

import de.fosd.jdime.artifact.Artifact;
import de.fosd.jdime.config.merge.MergeContext;
import de.fosd.jdime.config.merge.MergeScenario;
import de.fosd.jdime.config.merge.Revision;
import de.fosd.jdime.matcher.matching.Matching;
import de.fosd.jdime.operations.AddOperation;
import de.fosd.jdime.operations.ConflictOperation;
import de.fosd.jdime.operations.MergeOperation;

import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.fosd.jdime.config.merge.MergeScenario.BASE;

/**
 * Level-wise merge, i.e. merge argument lists one by one.
 *
 * @param <T> artifact type.
 * @author paul
 */
public class LevelwiseMerge<T extends Artifact<T>> extends BasicMerge<T> implements MergeInterface<T> {

    private static final Logger LOG = Logger.getLogger(OrderedMerge.class.getCanonicalName());

    /**
     * Level-wise merge.
     * <p>
     * Merge argument list L with R, representing left and right versions respectively.
     * L and R have equivalent length and matched pair-wise.
     * The strategy is simple: take each (l, r) pair and perform a 2 or 3-way merge.
     *
     * @param operation the <code>MergeOperation</code> to perform
     * @param context   the <code>MergeContext</code>
     */
    @Override
    public void merge(MergeOperation<T> operation, MergeContext context) {
        MergeScenario<T> triple = operation.getMergeScenario();
        T left = triple.getLeft();
        T base = triple.getBase();
        T right = triple.getRight();
        T target = operation.getTarget();

        assert (left.matches(right));
        assert (left.hasMatching(right) && right.hasMatching(left));

        // Left and right should have equivalent number of children.
        assert (left.getNumChildren() == right.getNumChildren());

        Revision l = left.getRevision();
        Revision b = base.getRevision();
        Revision r = right.getRevision();

        Iterator<T> leftIt = left.getChildren().iterator();
        Iterator<T> rightIt = right.getChildren().iterator();

        while (leftIt.hasNext()) {
            T leftChild = leftIt.next();
            T rightChild = rightIt.next();

            if (leftChild.matches(rightChild) && rightChild.matches(leftChild)) {
                LOG.fine(String.format("Level-wise: %s, %s", show(leftChild), show(rightChild)));
                twoOrThreeWayMerge(leftChild, rightChild, target, b, context);
                continue;
            }

            // leftChild and rightChild are not matched with each other
            Matching<T> mBase = leftChild.getMatching(b);
            T baseChild = mBase == null ? leftChild.createEmptyArtifact(BASE)
                    : mBase.getMatchingArtifact(leftChild);

            if (LOG.isLoggable(Level.FINE)) {
                LOG.warning(String.format("Level-wise: NOT matched: %s, %s", show(leftChild), show(rightChild)));
            }

            if (b != null && !leftChild.hasChanges(b)) { // `leftChild` = base, `rightChild` = target
                LOG.fine(String.format("Level-wise: %s is a change", show(leftChild)));

                // add the right change
                AddOperation<T> addOp = new AddOperation<>(rightChild, target, r.getName());
                rightChild.setMerged();
                addOp.apply(context);
            } else if (b != null && !rightChild.hasChanges(b)) { // `rightChild` = base, `leftChild` = target
                LOG.fine(String.format("Level-wise: %s is a change", show(rightChild)));

                // add the left change
                AddOperation<T> addOp = new AddOperation<>(leftChild, target, r.getName());
                leftChild.setMerged();
                addOp.apply(context);
            } else {
                // either insertion-deletion-conflict or deletion-insertion-conflict
                ConflictOperation<T> conflictOp = new ConflictOperation<>(
                        leftChild, rightChild, target, l.getName(), r.getName(), baseChild);
                conflictOp.apply(context);
            }
        }
    }

}