package geniusweb.sampleagent;

import geniusweb.issuevalue.Value;

import java.util.Comparator;

public class Node {
    public Value valueOfIssue;
    public int weightSum = 0;
    public int count = 0;
    public double meanWeightSum = 0.0f; // counts #occurences of this value.

    public Node(Value value) {
        this.valueOfIssue = value;
    }

    public String toString() {
        return String.format("%s %f", valueOfIssue, meanWeightSum);
    }

    // Overriding the comparator interface
    static class meanWeightSumComparator implements Comparator<Node> {
        public int compare(Node o1, Node o2) {
            if (o1.meanWeightSum < o2.meanWeightSum) {
                return 1;
            } else if (o1.meanWeightSum > o2.meanWeightSum) {
                return -1;
            }
            return 0;
        }
    }

}
