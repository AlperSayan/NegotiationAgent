package geniusweb.sampleagent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.issuevalue.Value;
import geniusweb.issuevalue.ValueSet;


@SuppressWarnings("serial")
public class Graph extends HashMap<String, List<Node>> {
    private Domain domain;

    // importance map
    public Graph(Domain domain) {
        super();
        this.domain = domain;
        // Create empty my import map, empty
        for (String issue : domain.getIssues()) {
            ValueSet values = domain.getValues(issue);
            List<Node> issueImpUnit = new ArrayList<>();
            for (Value value : values) {
                issueImpUnit.add(new Node(value));
            }
            this.put(issue, issueImpUnit);
        }
    }


    public void self_update(List<Bid> bidOrdering) {
        int currentWeight = 0;
        for (Bid bid : bidOrdering) {
            currentWeight += 1;
            for (String issue : bid.getIssues()) {
                List<Node> currentIssueList = this.get(issue);
                for (Node currentUnit : currentIssueList) {
                    if (currentUnit.valueOfIssue.toString()
                            .equals(bid.getValue(issue).toString())) {
                        currentUnit.weightSum += currentWeight;
                        currentUnit.count += 1;
                        break;
                    }
                }
            }
        }
        // Calculate weights
        for (List<Node> NodeList : this.values()) {
            for (Node currentUnit : NodeList) {
                if (currentUnit.count == 0) {
                    currentUnit.meanWeightSum = 0.0;
                } else {
                    currentUnit.meanWeightSum = (double) currentUnit.weightSum
                            / (double) currentUnit.count;
                }
            }
        }
        // Sort
        for (List<Node> NodeList : this.values()) {
            NodeList.sort(new Node.meanWeightSumComparator());
        }
        // Find the minimum
        double minMeanWeightSum = Double.POSITIVE_INFINITY;
        for (Map.Entry<String, List<Node>> entry : this.entrySet()) {
            double tempMeanWeightSum = entry.getValue()
                    .get(entry.getValue().size() - 1).meanWeightSum;
            if (tempMeanWeightSum < minMeanWeightSum) {
                minMeanWeightSum = tempMeanWeightSum;
            }
        }
        // Minus all values
        for (List<Node> NodeList : this.values()) {
            for (Node currentUnit : NodeList) {
                currentUnit.meanWeightSum -= minMeanWeightSum;
            }
        }
    }

    public double getImportance(Bid bid) {
        double bidImportance = 0.0;
        for (String issue : bid.getIssues()) {
            Value value = bid.getValue(issue);
            double valueImportance = 0.0;
            for (Node i : this.get(issue)) {
                if (i.valueOfIssue.equals(value)) {
                    valueImportance = i.meanWeightSum;
                    break;
                }
            }
            bidImportance += valueImportance;
        }
        return bidImportance;
    }
}
