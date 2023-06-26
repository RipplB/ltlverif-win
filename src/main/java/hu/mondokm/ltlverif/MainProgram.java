package hu.mondokm.ltlverif;

import hu.bme.mit.theta.analysis.pred.PredPrec;
import hu.bme.mit.theta.cfa.*;
import hu.bme.mit.theta.cfa.dsl.CfaDslManager;
import hu.bme.mit.theta.core.decl.VarDecl;
import hu.bme.mit.theta.core.type.inttype.IntType;
import hu.mondokm.ltlverif.antlr.*;
import hu.mondokm.ltlverif.buchi.AutomatonBuilder;
import hu.mondokm.ltlverif.buchi.BuchiAutomaton;
import hu.mondokm.ltlverif.cfa.CfaSUT;
import hu.mondokm.ltlverif.xsts.XSTS;
import hu.mondokm.ltlverif.xsts.XSTSSUT;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static hu.bme.mit.theta.core.type.abstracttype.AbstractExprs.Eq;
import static hu.bme.mit.theta.core.type.inttype.IntExprs.Int;

import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.hoa.HoaWriter;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.translations.ltl2nba.SymmetricNBAConstruction;

public class MainProgram {

    public static void main(String[] args){
        try {
            long startTime = System.currentTimeMillis();
            long beforeUsedMem=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();

            InputStream inputStream = new FileInputStream("src/main/resources/cfa/counter5_false_err.cfa");
            CFA cfa = CfaDslManager.createCfa(inputStream);
            HashMap<String,VarDecl> vars=new HashMap<String, VarDecl>();
            for(VarDecl decl:cfa.getVars()){
                vars.put(decl.getName(),decl);
            }
            HashMap<String,Integer> literalToIntMap = new HashMap<String, Integer>();


//            XSTSGrammarLexer lexer=new XSTSGrammarLexer(CharStreams.fromFileName("src/main/resources/xsts/orthogonal.xsts"));
//            CommonTokenStream tokenStream=new CommonTokenStream(lexer);
//            XSTSGrammarParser parser=new XSTSGrammarParser(tokenStream);
//            XSTSGrammarParser.XstsContext model =parser.xsts();
//            XSTSVisitor visitor=new XSTSVisitor();
//            visitor.visitXsts(model);
//            XSTS xsts=visitor.getXsts();
//
//            HashMap<String,VarDecl> vars=new HashMap<String, VarDecl>();
//            for(VarDecl decl:xsts.getVars()){
//                vars.put(decl.getName(),decl);
//            }
//
//            HashMap<String,Integer> literalToIntMap= visitor.getLiteralToIntMap();

            String text="G (not err)";
            LTLGrammarLexer ltlLexer=new LTLGrammarLexer(CharStreams.fromString(text));
            CommonTokenStream ltlTokenStream=new CommonTokenStream(ltlLexer);
            LTLGrammarParser ltlParser=new LTLGrammarParser(ltlTokenStream);
            LTLGrammarParser.ModelContext ltlModel =ltlParser.model();
            ToStringVisitor toStringVisitor=new ToStringVisitor(new APGeneratorVisitor(vars, literalToIntMap));
            System.out.println();
            String ltlExpr=toStringVisitor.visitModel(ltlModel);
            System.out.println(ltlExpr);
            System.out.println(toStringVisitor.getAps().values());

            LabelledFormula formula = LtlParser.parse(ltlExpr).not();
            System.out.println(formula);
            Automaton oautomaton = SymmetricNBAConstruction.of(BuchiAcceptance.class).apply(formula);

            String automatonStr = HoaWriter.toString(oautomaton);
            System.out.println(automatonStr);
    //            ProcessBuilder processBuilder=new ProcessBuilder("/usr/bin/ltl2tgba","!("+ltlExpr+")", "-CB");
//            processBuilder.redirectOutput(new File("src/main/resources/automata/out.hoa"));
//            processBuilder.redirectError(new File("error.txt"));
//            Process process=processBuilder.start();
//            process.waitFor();
//
            AutomatonBuilder builder=new AutomatonBuilder();
            builder.setAps(toStringVisitor.getAps());
//            BuchiAutomaton automaton=builder.parseAutomatonFromFile("src/main/resources/automata/out.hoa");
            BuchiAutomaton automaton = builder.parseAutomatonFromString(automatonStr);


//            boolean result=CegarVerifier.verifySUT(new XSTSSUT(xsts),automaton, PredPrec.of(toStringVisitor.getAps().values()));
            boolean result=CegarVerifier.verifySUT(new CfaSUT(cfa),automaton, PredPrec.of(toStringVisitor.getAps().values()));

            long stopTime = System.currentTimeMillis();
            long afterUsedMem=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();

            long elapsedTime = stopTime - startTime;
            long actualMemUsed=afterUsedMem-beforeUsedMem;

            System.out.println();
            System.out.println(result?"Ltl expression holds":"Ltl expression does not hold");
            System.out.println(elapsedTime/1000f + " s");
            System.out.println(actualMemUsed/1000000f+" MB");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
