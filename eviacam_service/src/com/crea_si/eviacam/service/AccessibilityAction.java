package com.crea_si.eviacam.service;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

class AccessibilityAction {

    /*
     * class to put together an accessibility action 
     * and the label to display to the user 
     */
    private static class ActionLabel {
        int action;
        int labelId;
        ActionLabel(int a, int l) {
            action= a;
            labelId= l;
        }
    }
    
    // store the pairs (accessibility action, label) of supported actions
    private static final ActionLabel[] mActionLabels = new ActionLabel[] {
        new ActionLabel(AccessibilityNodeInfo.ACTION_CLICK, R.string.click),
        new ActionLabel(AccessibilityNodeInfo.ACTION_LONG_CLICK, R.string.long_click),            
        new ActionLabel(AccessibilityNodeInfo.ACTION_COLLAPSE, R.string.collapse),
        new ActionLabel(AccessibilityNodeInfo.ACTION_COPY, R.string.copy),
        new ActionLabel(AccessibilityNodeInfo.ACTION_CUT, R.string.cut),            
        new ActionLabel(AccessibilityNodeInfo.ACTION_DISMISS, R.string.dismiss),            
        new ActionLabel(AccessibilityNodeInfo.ACTION_EXPAND, R.string.expand),
        new ActionLabel(AccessibilityNodeInfo.ACTION_PASTE, R.string.paste),
        new ActionLabel(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, R.string.scroll_backward),
        new ActionLabel(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, R.string.scroll_forward)
    };

    // accessibility actions we are interested on when searching nodes
    private final int FULL_ACTION_MASK;
    
    // layer view for context menu
    private ControlsLayerView mControlsLayerView;
    
    // layer view for docking panel
    private DockPanelLayerView mDockPanelLayerView;
    
    // tracks whether the contextual menu is open
    private boolean mContextMenuOpen= false;

    // node on which the action should be performed
    private AccessibilityNodeInfo mNode;
    
    public AccessibilityAction (ControlsLayerView cv, DockPanelLayerView dplv) {
        mControlsLayerView= cv;
        mDockPanelLayerView= dplv;
        
        // populate actions to view & compute action mask
        int full_action_mask= 0;
        for (ActionLabel al : mActionLabels) {
            mControlsLayerView.populateContextMenu(al.action, al.labelId);
            full_action_mask|= al.action;
        }
        
        FULL_ACTION_MASK= full_action_mask;
    }

    /*
     * manage global actions, return false if action not generated
     */
    private boolean manageGlobalActions (Point p) {
        int idDockPanelAction= mDockPanelLayerView.getViewIdBelowPoint(p);
        if (idDockPanelAction == View.NO_ID) return false;
        
        if (mDockPanelLayerView.performClick(idDockPanelAction)) return true;
        
        AccessibilityService s= EViacamService.getInstance();
        
        switch (idDockPanelAction) {
        case R.id.back_button:
            s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            break;
        case R.id.home_button:
            s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
            break;
        case R.id.recents_button:
            s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
            break;
        default:
            return false;
        }
        
        return true;
    }
    
    public void performAction (PointF p) {
        // TODO: consider making it an attribute
        Point pInt= new Point();
        pInt.x= (int) p.x;
        pInt.y= (int) p.y;
        
        if (mContextMenuOpen) {
            int action= mControlsLayerView.testClick(pInt);
            mControlsLayerView.hideContextMenu();
            mContextMenuOpen= false;
            if (action != 0) mNode.performAction(action);
        }
        else {
           
            if (manageGlobalActions(pInt)) return;
            
            AccessibilityNodeInfo node= findActionable (pInt, FULL_ACTION_MASK);
            
            if (node == null) return;
            
            EVIACAM.debug("Actionable node found: (" + p.x + ", " + p.y + ")." + 
                    AccessibilityNodeDebug.getNodeInfo(node));
            
            int availableActions= FULL_ACTION_MASK & node.getActions();
            
            if (Integer.bitCount(availableActions)> 1) {
                mControlsLayerView.showContextMenu(pInt, availableActions);
                mContextMenuOpen= true;
                mNode= node;
            }
            else {
                node.performAction(availableActions);
            }
        }
    }
    
    
    /*
     * class to store information across recursive calls
     */
    private static class RecursionInfo {
        final public Point p;
        final public Rect tmp= new Rect();
        final public int actions;
        
        RecursionInfo (Point p, int actions) {
            this.p = p;
            this.actions= actions;
        }
    }
 
    /*
     * find recursively the node under (x, y) that accepts some or all 
     * actions encoded on the mask
     */
    private static AccessibilityNodeInfo findActionable (Point p, int actions) {
        // get root node
        final AccessibilityNodeInfo rootNode = 
                EViacamService.getInstance().getRootInActiveWindow();
        if (rootNode == null) return null;
        
        // TODO: consider making it an attribute to avoid creating it each time
        RecursionInfo ri= new RecursionInfo (p, actions);
        
        return findActionable0(rootNode, ri);
    }
    
    /*
     * actual recursive call 
     */
    private static AccessibilityNodeInfo findActionable0(
            AccessibilityNodeInfo node, RecursionInfo ri) {

        // sometimes, during the recursion, getChild() might return null
        // check here and abort recursion in that case
        if (node == null) return null;
        
        node.getBoundsInScreen(ri.tmp);
        if (!ri.tmp.contains(ri.p.x, ri.p.y)) {
            // if window does not contain (x, y) stop recursion
            return null;
        }

        AccessibilityNodeInfo result = null;

        if ((node.getActions() & ri.actions) != 0) {
            // this is a good candidate but continue exploring children
            // there are controls such as ListView which are clickable
            // but do not have an useful action associated
            result = node;
        }

        // propagate calls to children
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = findActionable0(node.getChild(i), ri);

            if (child != null) result = child;
        }

        return result;
    }
}