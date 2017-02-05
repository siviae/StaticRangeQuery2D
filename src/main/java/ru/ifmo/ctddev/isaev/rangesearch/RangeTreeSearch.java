package ru.ifmo.ctddev.isaev.rangesearch;

import ru.ifmo.ctddev.isaev.rangesearch.node.AssocPoint;
import ru.ifmo.ctddev.isaev.rangesearch.node.Leaf;
import ru.ifmo.ctddev.isaev.rangesearch.node.Node;
import ru.ifmo.ctddev.isaev.rangesearch.node.Pair;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static ru.ifmo.ctddev.isaev.util.PointComparators.*;


/**
 * @author iisaev
 */
public class RangeTreeSearch extends RangeSearch {
    private Node tree;

    public RangeTreeSearch(List<Point> points) {
        super(points);
        points.sort(BY_X);
        tree = buildNode(points);
        boolean f = false;
    }

    private Node buildNode(List<Point> points) {
        if (points.size() > 2) {
            return buildComplexNode(points);
        } else if (points.size() == 2) {
            Point childPoint = points.get(0);
            Point parentPoint = points.get(1);
            Leaf child = new Leaf(childPoint);
            return new Node(child, null, parentPoint.x,
                    singletonList(new AssocPoint(parentPoint, 0, -1))
            );
        } else {
            return new Leaf(points.get(0));
        }
    }

    private Node buildComplexNode(List<Point> points) {
        List<Point> sortedByY = new ArrayList<>(points);
        sortedByY.sort(BY_Y);

        int medianIndex = points.size() / 2;
        Point median = points.get(medianIndex);
        List<Point> leftList = points.subList(0, medianIndex + 1);
        List<Point> leftSortedByY = new ArrayList<>(leftList);
        leftSortedByY.sort(BY_Y);
        Node left = buildNode(leftList);

        List<Point> rightList = points.subList(medianIndex + 1, points.size());
        List<Point> rightSortedByY = new ArrayList<>(rightList);
        rightSortedByY.sort(BY_Y);
        Node right = buildNode(rightList);

        List<AssocPoint> assoc = new ArrayList<>();
        sortedByY.forEach(point -> {
            Integer leftIndex = getAssocIndex(leftSortedByY, point);
            Integer rightIndex = getAssocIndex(rightSortedByY, point);
            assoc.add(new AssocPoint(point, leftIndex, rightIndex));
        });

        assoc.sort(ASSOC_BY_Y);
        return new Node(left, right, median.x, assoc);
    }

    private Integer getAssocIndex(List<Point> leftSortedByY, Point point) {
        int pos = Collections.binarySearch(leftSortedByY, point, BY_Y);
        int res = pos >= 0 ? pos : -pos - 1;
        return res == leftSortedByY.size() ? null : res;
    }

    private Integer getAssocIndex(List<AssocPoint> leftSortedByY, AssocPoint point) {
        int pos = Collections.binarySearch(leftSortedByY, point, ASSOC_BY_Y);
        int res = pos >= 0 ? pos : -pos - 1;
        return res == leftSortedByY.size() ? null : res;
    }

    @Override
    public List<Point> query(Point point1, Point point2) {
        int fromX = Math.min(point1.x, point2.x);
        int toX = Math.max(point1.x, point2.x);
        int fromY = Math.min(point1.y, point2.y);
        int toY = Math.max(point1.y, point2.y);
        Node vSplit = findSplitNode(fromX, toX, tree);
        if (vSplit == null) {
            return emptyList();
        }
        Integer yStartIndex = getAssocIndex(vSplit.getAssoc(), new AssocPoint(new Point(-1, fromY), null, null));
        if (vSplit.isLeaf() && yStartIndex != null) {
            return singletonList(vSplit.getAssoc().get(0).getPoint());
        }

        AssocPoint parentPoint = vSplit.getAssoc().get(yStartIndex);
        List<Pair<List<AssocPoint>, Integer>> subtrees = new ArrayList<>();
        traverseToLeft(vSplit.getLeft(), fromX, parentPoint.getLeftIndex(), subtrees);
        traverseToRight(vSplit.getRight(), toX, parentPoint.getRightIndex(), subtrees);
        List<Point> result = new ArrayList<>();
        subtrees.forEach(pair -> {
            List<AssocPoint> list = pair.getFirst();
            Integer index = pair.getSecond();
            if (index != null) {
                while (index < list.size() && list.get(index).getPoint().y <= toY) {
                    result.add(list.get(index).getPoint());
                    ++index;
                }
            }
        });
        return result;
    }

    private void traverseToRight(Node node, int toX, Integer assocIndex, List<Pair<List<AssocPoint>, Integer>> subtrees) {
        if (node == null) {
            return;
        }
        if (assocIndex == null) {
            return;
        }
        if (node.isLeaf() && toX >= node.getxCoord()) {
            subtrees.add(new Pair<>(node.getAssoc(), assocIndex));
            return;
        }
        AssocPoint currentPoint = node.getAssoc().get(assocIndex);
        if (toX > node.getxCoord()) {
            if (node.getLeft() != null) { // almost never happen
                subtrees.add(new Pair<>(node.getLeft().getAssoc(), currentPoint.getLeftIndex()));
            }
            traverseToRight(node.getRight(), toX, currentPoint.getRightIndex(), subtrees);
        } else {
            traverseToRight(node.getLeft(), toX, currentPoint.getLeftIndex(), subtrees);
        }
    }

    private void traverseToLeft(Node node, int fromX, Integer assocIndex, List<Pair<List<AssocPoint>, Integer>> subtrees) {
        if (node == null) {
            return;
        }
        if (assocIndex == null) {
            return;
        }
        if (node.isLeaf()) {
            if (fromX <= node.getxCoord()) {
                subtrees.add(new Pair<>(node.getAssoc(), assocIndex));
            }
            return;
        }
        AssocPoint currentPoint = node.getAssoc().get(assocIndex);
        if (fromX <= node.getxCoord()) {
            if (node.getRight() != null) {
                subtrees.add(new Pair<>(node.getRight().getAssoc(), currentPoint.getRightIndex()));
            }
            traverseToLeft(node.getLeft(), fromX, currentPoint.getLeftIndex(), subtrees);
        } else {
            traverseToLeft(node.getRight(), fromX, currentPoint.getRightIndex(), subtrees);
        }
    }

    private Node findSplitNode(int fromX, int toX, Node tree) {
        if (tree == null) {
            return null;
        }
        if (fromX <= tree.getxCoord() && toX > tree.getxCoord()) {
            return tree;
        }
        if (toX <= tree.getxCoord()) {
            return findSplitNode(fromX, toX, tree.getLeft());
        } else {
            return findSplitNode(fromX, toX, tree.getRight());
        }
    }
}
