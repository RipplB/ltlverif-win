package hu.mondokm.ltlverif.abstractor;

import hu.bme.mit.theta.analysis.Trace;
import hu.bme.mit.theta.analysis.expr.ExprAction;
import hu.bme.mit.theta.analysis.expr.ExprState;
import hu.bme.mit.theta.analysis.pred.PredPrec;
import hu.bme.mit.theta.analysis.pred.PredState;
import hu.bme.mit.theta.analysis.unit.UnitState;
import hu.bme.mit.theta.cfa.CFA;
import hu.bme.mit.theta.cfa.analysis.CfaAction;
import hu.mondokm.ltlverif.buchi.CfaBuchiTransFunc;
import hu.mondokm.ltlverif.buchi.BuchiAction;
import hu.mondokm.ltlverif.buchi.BuchiAutomaton;
import hu.mondokm.ltlverif.buchi.BuchiState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

import static hu.bme.mit.theta.core.type.booltype.BoolExprs.True;

public class NDFSAbstractor implements LtlAbstractor {

    private CFA cfa;
    private BuchiAutomaton automaton;

    private HashMap<ProductState, NDFSData> redblue=new HashMap<ProductState, NDFSData>();
    private Stack<ProductState> stack=new Stack<ProductState>();
    private Stack<ProductState> cycle=new Stack<ProductState>();
    private ProductState sccStart=null;
    private PredPrec precision;
    private boolean foundCEX=false;
    private InfTrace result=null;

    private NDFSAbstractor(CFA cfa, BuchiAutomaton automaton){
        this.cfa=cfa;
        this.automaton=automaton;
    }

    public static NDFSAbstractor create(CFA cfa, BuchiAutomaton automaton){
        return new NDFSAbstractor(cfa,automaton);
    }

    public class NDFSData{
        private boolean blue=false,red=false;
        public NDFSData(boolean blue, boolean red){
            this.red=red;
            this.blue=blue;
        }
    }

    public InfTrace check(PredPrec precision) {
        this.precision=precision;
        foundCEX=false;
        result=null;
        CFA.Loc loc=cfa.getInitLoc();
        BuchiState starting=automaton.getInitial();
        stack.clear();
        cycle.clear();
        redblue.clear();

        dfs_blue(new ProductState(null,loc, PredState.of(True()), starting, new BuchiAction(True(),null)));

        return result;
    }

    public void dfs_blue(ProductState curr){
        stack.push(curr);
        if(redblue.get(curr)==null)redblue.put(curr,new NDFSData(true,false));
        else redblue.get(curr).blue=true;
        for(ProductState next: CfaBuchiTransFunc.nextStates(curr,precision)){
            if(redblue.get(next)==null || redblue.get(next).blue==false) dfs_blue(next);
            if(foundCEX){
                return;
            }
        }
        if(curr.getBuchiState().isAccepting()){
            sccStart=curr;
            dfs_red(curr);
        }
        stack.pop();
    }

    public void dfs_red(ProductState curr){
        if(redblue.get(curr)==null)redblue.put(curr,new NDFSData(false,true));
        else redblue.get(curr).red=true;
        for(ProductState next:CfaBuchiTransFunc.nextStates(curr,precision)){
            cycle.push(next);
            if(redblue.get(next)==null || redblue.get(next).red==false) dfs_red(next);
            if(foundCEX) {
                return;
            }
            if (next.equals(sccStart)) {
                List<ExprAction> edges=new ArrayList<ExprAction>();
                List<ExprState> states=new ArrayList<ExprState>();
                states.add(UnitState.getInstance());
                for(ProductState prod: stack) {
                    if(prod.getToEdge()!=null) edges.add(CfaAction.create(prod.getToEdge())); else edges.add(new BuchiAction(True(),null));
                    if(prod.getCond()!=null) edges.add(prod.getCond()); else edges.add(new BuchiAction(True(),null));
                    states.add(prod.getPredState());
                    states.add(prod.getPredState());
                }

                int cycleStart=states.size()-1;

                for(ProductState prod: cycle) {
                    if(prod.getToEdge()!=null) edges.add(CfaAction.create(prod.getToEdge())); else edges.add(new BuchiAction(True(),null));
                    if(prod.getCond()!=null) edges.add(prod.getCond()); else edges.add(new BuchiAction(True(),null));
                    states.add(prod.getPredState());
                    states.add(prod.getPredState());
                }

//                Trace<ExprState,ExprAction> trace= ExprTraceUtils.traceFrom(edges);
                result= InfTrace.create(Trace.of(states,edges),cycleStart);

                foundCEX=true;

            }
            cycle.pop();


        }
    }
}
