package megamek.common.pathfinder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import megamek.common.BulldozerMovePath;
import megamek.common.Coords;
import megamek.common.Entity;
import megamek.common.IBoard;
import megamek.common.MovePath;
import megamek.common.MovePath.MoveStepType;

public class DestructionAwareDestinationPathfinder extends BoardEdgePathFinder {

    Comparator<BulldozerMovePath> movePathComparator;
    int maximumCost = Integer.MAX_VALUE;
    
    /**
     */
    public MovePath findPathToCoords(Entity entity, Coords destinationCoords) {
        BulldozerMovePath startPath = new BulldozerMovePath(entity.getGame(), entity);
        if(entity.hasETypeFlag(Entity.ETYPE_INFANTRY)) {
            startPath.addStep(MoveStepType.CLIMB_MODE_OFF);
        } else {
            startPath.addStep(MoveStepType.CLIMB_MODE_ON);
        }

        movePathComparator = new AStarComparator(destinationCoords);
        
        List<BulldozerMovePath> candidates = new ArrayList<>();
        candidates.add(startPath);

        // a collection of coordinates we've already visited, so we don't loop back.
        Map<Coords, BulldozerMovePath> shortestPathsToCoords = new HashMap<>();
        shortestPathsToCoords.put(startPath.getFinalCoords(), startPath);
        BulldozerMovePath bestPath = new BulldozerMovePath(entity.getGame(), entity);

        while(!candidates.isEmpty()) {            
            candidates.addAll(generateChildNodes(candidates.get(0), shortestPathsToCoords));
            
            if(candidates.get(0).getFinalCoords().equals(destinationCoords) &&
                    movePathComparator.compare(bestPath, candidates.get(0)) < 0) {
                bestPath = candidates.get(0);
                maximumCost = bestPath.getMpUsed() + bestPath.getLevelingCost();
            }

            candidates.remove(0);
            candidates.sort(movePathComparator);
        }
  
        return bestPath;
    }
    
    /**
     * Function that generates all possible "legal" moves resulting from the given path
     * and updates the set of visited coordinates so we don't visit them again.
     * @param parentPath The path for which to generate child nodes
     * @param visitedCoords Set of visited coordinates so we don't loop around
     * @return List of valid children. Between 0 and 3 inclusive.
     */
    protected List<BulldozerMovePath> generateChildNodes(BulldozerMovePath parentPath, Map<Coords, BulldozerMovePath> shortestPathsToCoords) {
        List<BulldozerMovePath> children = new ArrayList<>();

        // the children of a move path are:
        //      turn left and step forward one
        //      step forward one
        //      turn right and step forward one
        BulldozerMovePath leftChild = (BulldozerMovePath) parentPath.clone();
        leftChild.addStep(MoveStepType.TURN_LEFT);
        leftChild.addStep(MoveStepType.FORWARDS);
        processChild(leftChild, children, shortestPathsToCoords);
        
        BulldozerMovePath leftleftChild = (BulldozerMovePath) parentPath.clone();
        leftleftChild.addStep(MoveStepType.TURN_LEFT);
        leftleftChild.addStep(MoveStepType.TURN_LEFT);
        leftleftChild.addStep(MoveStepType.FORWARDS);
        processChild(leftleftChild, children, shortestPathsToCoords);
        
        BulldozerMovePath centerChild = (BulldozerMovePath) parentPath.clone();
        centerChild.addStep(MoveStepType.FORWARDS);
        processChild(centerChild, children, shortestPathsToCoords);

        BulldozerMovePath rightChild = (BulldozerMovePath) parentPath.clone();
        rightChild.addStep(MoveStepType.TURN_RIGHT);
        rightChild.addStep(MoveStepType.FORWARDS);
        processChild(rightChild, children, shortestPathsToCoords);
        
        BulldozerMovePath rightrightChild = (BulldozerMovePath) parentPath.clone();
        rightrightChild.addStep(MoveStepType.TURN_RIGHT);
        rightrightChild.addStep(MoveStepType.TURN_RIGHT);
        rightrightChild.addStep(MoveStepType.FORWARDS);
        processChild(rightrightChild, children, shortestPathsToCoords);
        
        BulldozerMovePath rightrightrightChild = (BulldozerMovePath) parentPath.clone();
        rightrightrightChild.addStep(MoveStepType.TURN_RIGHT);
        rightrightrightChild.addStep(MoveStepType.TURN_RIGHT);
        rightrightrightChild.addStep(MoveStepType.TURN_RIGHT);
        rightrightrightChild.addStep(MoveStepType.FORWARDS);
        processChild(rightrightrightChild, children, shortestPathsToCoords);

        return children;
    }
    
    /**
     * Helper function that handles logic related to potentially adding a generated child path
     * to the list of child paths.
     */
    protected void processChild(BulldozerMovePath child, List<BulldozerMovePath> children, 
            Map<Coords, BulldozerMovePath> shortestPathsToCoords) {
        // (if we haven't visited these coordinates before
        // or we have, and this is a shorter path)
        // and (it is a legal move
        // or it needs some "terrain adjustment" to become a legal move)
        // and we haven't already found a path to the destination that's cheaper than what we're considering
        // and we're not going off board 
        
        if((!shortestPathsToCoords.containsKey(child.getFinalCoords()) ||
                // shorter path to these coordinates
                (movePathComparator.compare(shortestPathsToCoords.get(child.getFinalCoords()), child) > 0)) &&
                // legal or needs leveling and not off-board
                (isLegalMove((MovePath) child) || (child.needsLeveling() && child.getGame().getBoard().contains(child.getFinalCoords()))) &&
                // better than existing path to ultimate destination
                (child.getMpUsed() + child.getLevelingCost() < maximumCost)) {
            shortestPathsToCoords.put(child.getFinalCoords(), child);
            children.add(child);
        }
    }
    
    /**
     * Comparator implementation useful in comparing how much closer a given path is to the internal
     * "destination edge" than the other.
     * @author NickAragua
     *
     */
    private class AStarComparator implements Comparator<BulldozerMovePath> {
        private Coords destination;

        /**
         * Constructor - initializes the destination edge.
         * @param targetRegion Destination edge
         */
        public AStarComparator(Coords destination) {
            this.destination = destination;
        }
        
        /**
         * compare the first move path to the second
         * Favors paths that move closer to the destination edge first.
         * in case of tie, favors paths that cost less MP
         */
        public int compare(BulldozerMovePath first, BulldozerMovePath second) {
            IBoard board = first.getGame().getBoard();
            boolean backwards = false;//first.getStep(0).getS == MoveStepType.BACKWARDS;
            int h1 = first.getFinalCoords().distance(destination)
                    + ShortestPathFinder.getFacingDiff(first, destination, backwards)
                    + ShortestPathFinder.getLevelDiff(first, destination, board)
                    + ShortestPathFinder.getElevationDiff(first, destination, board, first.getEntity());
            int h2 = second.getFinalCoords().distance(destination)
                    + ShortestPathFinder.getFacingDiff(second, destination, backwards)
                    + ShortestPathFinder.getLevelDiff(second, destination, board)
                    + ShortestPathFinder.getElevationDiff(second, destination, board, second.getEntity());
    
            int dd = (first.getMpUsed() + first.getLevelingCost() + h1) - (second.getMpUsed() + second.getLevelingCost() + h2);
    
            if (dd != 0) {
                return dd;
            } else {
                return first.getHexesMoved() - second.getHexesMoved();
            }           
        }
    }
}
