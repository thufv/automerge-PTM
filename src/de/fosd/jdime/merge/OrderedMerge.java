package de.fosd.jdime.merge;

import de.fosd.jdime.artifact.Artifact;
import de.fosd.jdime.config.merge.MergeContext;
import de.fosd.jdime.config.merge.MergeScenario;
import de.fosd.jdime.config.merge.Revision;
import de.fosd.jdime.operations.ConflictOperation;
import de.fosd.jdime.operations.MergeOperation;

import java.util.ArrayList;
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

        Iterator<T> leftIt = leftList.iterator();
        Iterator<T> rightIt = rightList.iterator();
        T leftChild = leftIt.next();
        T rightChild = rightIt.next();

        List<T> suspended = new ArrayList<>();

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
                LOG.fine(String.format("Ordered: skip merged: %s", show(leftChild)));

                if (leftIt.hasNext()) {
                    leftChild = leftIt.next();
                } else {
                    leftChild = null;
                }
                continue;
            }
            if (rightChild != null && rightChild.isMerged()) { // 1) right is merged
                LOG.fine(String.format("Ordered: skip merged: %s", show(rightChild)));

                if (rightIt.hasNext()) {
                    rightChild = rightIt.next();
                } else {
                    rightChild = null;
                }
                continue;
            }

            if (leftChild != null &&
                    !leftChild.getProperMatch(r, context).isPresent()) { // 2) left has no proper match
                LOG.fine(String.format("Ordered: suspend: %s", show(leftChild)));
                suspended.add(leftChild);

                if (leftIt.hasNext()) {
                    leftChild = leftIt.next();
                } else {
                    leftChild = null;
                }
                continue;
            }
            if (rightChild != null &&
                    !rightChild.getProperMatch(l, context).isPresent()) { // 2) right has no proper match
                LOG.fine(String.format("Ordered: suspend: %s", show(rightChild)));
                suspended.add(rightChild);

                if (rightIt.hasNext()) {
                    rightChild = rightIt.next();
                } else {
                    rightChild = null;
                }
                continue;
            }

            if (leftChild != null && rightChild != null &&
                    leftChild.getProperMatch(r, context).get() == rightChild) {
                // first handle suspending ones
                handleSuspended(suspended, b, l, r, target, context);
                suspended.clear();

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

            LOG.severe("Ordered: assertion failure: skip not matched: " +
                    show(leftChild) + " <-> " + show(rightChild));
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
        }

        handleSuspended(suspended, b, l, r, target, context);
        suspended.clear();
    }

    private void handleSuspended(List<T> suspended, Revision b, Revision l, Revision r,
                                 T target, MergeContext context) {
        int leftCount = 0;
        int rightCount = 0;

        for (T n : suspended) {
            if (n.getRevision().equals(l)) {
                leftCount++;
            } else if (n.getRevision().equals(r)) {
                rightCount++;
            } else {
                LOG.severe("Ordered: handle suspend: WTF revision: " + n.getRevision());
            }
        }

        if (rightCount == 0) { // all left
            for (T n : suspended) {
                simpleMerge(n, target, context, l, r, b, true);
            }
            return;
        }
        if (leftCount == 0) { // all right
            for (T n : suspended) {
                simpleMerge(n, target, context, r, l, b, false);
            }
            return;
        }

        // conflict
        LOG.fine("Ordered: handle suspend: conflict");

        T lefts = target.copy();
        lefts.clearChildren();
        T rights = target.copy();
        rights.clearChildren();

        for (T n : suspended) {
            if (n.getRevision().equals(l)) {
                lefts.addChild(n);
            } else {
                rights.addChild(n);
            }
        }

        ConflictOperation<T> conflictOp = new ConflictOperation<>(
                lefts, rights, target, l.getName(), r.getName(), null, true);
        conflictOp.apply(context);
    }
}