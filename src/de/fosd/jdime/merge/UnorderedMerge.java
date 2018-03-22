package de.fosd.jdime.merge;

import de.fosd.jdime.artifact.Artifact;
import de.fosd.jdime.config.merge.MergeContext;
import de.fosd.jdime.config.merge.MergeScenario;
import de.fosd.jdime.config.merge.Revision;
import de.fosd.jdime.operations.MergeOperation;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Unordered merge.
 *
 * @param <T> type of artifact
 * @author paul
 */
public class UnorderedMerge<T extends Artifact<T>> extends BasicMerge<T> implements MergeInterface<T> {

    private static final Logger LOG = Logger.getLogger(UnorderedMerge.class.getCanonicalName());

    /**
     * Unordered merge for each element `left`.
     * Cases:
     * 1) If `left` is already merged, then skip.
     * 2) If `left` has no proper match, then we apply a simple merge.
     * 3) If `left` has a proper match, we apply a normal 3-way or 2-way merging according to the presence of b.
     *
     * @param left    current element.
     * @param target  target left.
     * @param context merge context.
     * @param l       revision of `left`.
     * @param r       opposing revision, of right.
     * @param b       base revision.
     * @param isLeft  whether `l` is left.
     */
    private void mergeOn(T left, T target, MergeContext context,
                         Revision l, Revision r, Revision b, boolean isLeft) {
        LOG.fine(String.format("Unordered: %s", show(left)));

        if (left.isMerged()) { // 1) already merged
            return;
        }

        Optional<T> p = left.getPair();
        if (p.isPresent()) { // 2) match with `paired`
            T paired = p.get();
            twoOrThreeWayMerge(left, paired, target, b, context);
        } else { // 3) no match
            simpleMerge(left, target, context, l, r, b, isLeft);
        }
    }

    /**
     * Unordered merge.
     * <p>
     * Merge list L with R, representing left and right versions respectively.
     * The top level procedure:
     * 1) merge on each element of L;
     * 2) merge on each element of R;
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

        while (leftIt.hasNext()) {
            T pivot = leftIt.next();
            mergeOn(pivot, target, context, l, r, b, true);
        }
        while (rightIt.hasNext()) {
            T pivot = rightIt.next();
            mergeOn(pivot, target, context, r, l, b, false);
        }
    }
}
