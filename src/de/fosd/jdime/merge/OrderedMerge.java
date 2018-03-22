package de.fosd.jdime.merge;

import de.fosd.jdime.artifact.Artifact;
import de.fosd.jdime.config.merge.MergeContext;
import de.fosd.jdime.config.merge.MergeScenario;
import de.fosd.jdime.config.merge.Revision;
import de.fosd.jdime.operations.MergeOperation;

import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Ordered merge.
 *
 * @param <T> type of artifact
 * @author paul
 */
public class OrderedMerge<T extends Artifact<T>> extends BasicMerge<T> implements MergeInterface<T> {

    private static final Logger LOG = Logger.getLogger(OrderedMerge.class.getCanonicalName());

    /**
     * Ordered merge.
     * <p>
     * Merge list L with R, representing left and right versions respectively.
     * The top level procedure: merge on each element of L/R.
     *
     * @param operation the <code>MergeOperation</code> to perform
     * @param context   the <code>MergeContext</code>
     */
    @Override
    public void merge(MergeOperation<T> operation, MergeContext context) {
        assert (operation != null);
        assert (context != null);

        MergeScenario<T> triple = operation.getMergeScenario();
        T left = triple.getLeft();
        T base = triple.getBase();
        T right = triple.getRight();
        T target = operation.getTarget();

        assert (left.matches(right));
        assert (left.hasMatching(right)) && right.hasMatching(left);

        Revision l = left.getRevision();
        Revision b = base.getRevision();
        Revision r = right.getRevision();

        List<T> leftList = left.getChildren();
        List<T> rightList = right.getChildren();

        pair(leftList, rightList, l, r);

        Iterator<T> leftIt = leftList.iterator();
        Iterator<T> rightIt = rightList.iterator();
        T leftChild = leftIt.next();
        T rightChild = rightIt.next();

        /**
         * Ordered merge for each element.
         * Cases:
         * 1) If left or right is already merged, then skip.
         * 2) If left or right has no proper match, then we apply a simple merge.
         * 3) If (left, right) forms a proper match, we apply a normal 3-way or 2-way merging
         * according to the presence of base.
         * 4)
         **/
        while (leftChild != null || rightChild != null) {
            if (leftChild != null && leftChild.isMerged()) { // 1) left is merged
                LOG.fine(String.format("Ordered: %s", show(leftChild)));

                if (leftIt.hasNext()) {
                    leftChild = leftIt.next();
                } else {
                    leftChild = null;
                }
                continue;
            }
            if (rightChild != null && rightChild.isMerged()) { // 1) right is merged
                LOG.fine(String.format("Ordered: %s", show(rightChild)));

                if (rightIt.hasNext()) {
                    rightChild = rightIt.next();
                } else {
                    rightChild = null;
                }
                continue;
            }

            if (leftChild != null && !leftChild.getPair().isPresent()) {// 2) left has no proper match
                LOG.fine(String.format("Ordered: %s", show(leftChild)));
                simpleMerge(leftChild, target, context, l, r, b, true);

                if (leftIt.hasNext()) {
                    leftChild = leftIt.next();
                } else {
                    leftChild = null;
                }
                continue;
            }
            if (rightChild != null && !rightChild.getPair().isPresent()) { // 2) right has no proper match
                LOG.fine(String.format("Ordered: %s", show(rightChild)));
                simpleMerge(rightChild, target, context, r, l, b, false);

                if (rightIt.hasNext()) {
                    rightChild = rightIt.next();
                } else {
                    rightChild = null;
                }
                continue;
            }

            if (leftChild != null && rightChild != null && leftChild.getPair().get() == rightChild) {
                // 3) (left, right) forms a proper match
                LOG.fine(String.format("Ordered: %s, %s", show(leftChild), show(rightChild)));
                twoOrThreeWayMerge(leftChild, rightChild, target, b, context);

                if (leftIt.hasNext()) {
                    leftChild = leftIt.next();
                } else {
                    leftChild = null;
                }
                if (rightIt.hasNext()) {
                    rightChild = rightIt.next();
                } else {
                    rightChild = null;
                }
                continue;
            }

            assert false;
        }
    }
}