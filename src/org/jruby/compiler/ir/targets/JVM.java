package org.jruby.compiler.ir.targets;

import jnr.constants.Constant;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import org.jruby.RubyInstanceConfig;
import org.jruby.RubyObject;
import org.jruby.compiler.ir.CompilerTarget;
import org.jruby.compiler.ir.IRClass;
import org.jruby.compiler.ir.IRMethod;
import org.jruby.compiler.ir.IRScope;
import org.jruby.compiler.ir.IRScript;
import org.jruby.compiler.ir.instructions.BEQInstr;
import org.jruby.compiler.ir.instructions.CallInstr;
import org.jruby.compiler.ir.instructions.CopyInstr;
import org.jruby.compiler.ir.instructions.DefineClassMethodInstr;
import org.jruby.compiler.ir.instructions.DefineInstanceMethodInstr;
import org.jruby.compiler.ir.instructions.GetFieldInstr;
import org.jruby.compiler.ir.instructions.Instr;
import org.jruby.compiler.ir.instructions.JumpInstr;
import org.jruby.compiler.ir.instructions.LABEL_Instr;
import org.jruby.compiler.ir.instructions.PutFieldInstr;
import org.jruby.compiler.ir.instructions.ReceiveArgumentInstruction;
import org.jruby.compiler.ir.instructions.ReturnInstr;
import org.jruby.compiler.ir.operands.FieldRef;
import org.jruby.compiler.ir.operands.Fixnum;
import org.jruby.compiler.ir.operands.Label;
import org.jruby.compiler.ir.operands.Operand;
import org.jruby.compiler.ir.operands.Variable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.util.TraceClassVisitor;
import static org.objectweb.asm.Opcodes.*;
import static org.jruby.util.CodegenUtils.*;
import static org.objectweb.asm.commons.GeneratorAdapter.*;

// This class represents JVM as the target of compilation
// and outputs bytecode
public class JVM implements CompilerTarget {
    private static final boolean DEBUG = true;
    
    Stack<ClassData> clsStack = new Stack();
    List<ClassData> clsAccum = new ArrayList();
    IRScript script;

    private static class ClassData {
        public ClassData(ClassVisitor cls) {
            this.cls = cls;
        }

        public GeneratorAdapter method() {
            return methodData().method;
        }

        public MethodData methodData() {
            return methodStack.peek();
        }

        public void pushmethod(String name) {
            methodStack.push(new MethodData(new GeneratorAdapter(
                    ACC_PUBLIC | ACC_STATIC,
                    Method.getMethod("org.jruby.runtime.builtin.IRubyObject " + name + " (org.jruby.runtime.ThreadContext, org.jruby.runtime.builtin.IRubyObject)"),
                    null,
                    null,
                    cls)));
        }

        public void popmethod() {
            method().endMethod();
            methodStack.pop();
        }
        
        public ClassVisitor cls;
        Stack<MethodData> methodStack = new Stack();
        public Set<String> fieldSet = new HashSet<String>();
    }

    private static class MethodData {
        public MethodData(GeneratorAdapter method) {
            this.method = method;
        }
        public GeneratorAdapter method;
        public Map<Variable, Integer> varMap = new HashMap<Variable, Integer>();
        public Map<Label, org.objectweb.asm.Label> labelMap = new HashMap<Label, org.objectweb.asm.Label>();
    }

/**
    public static void main(String[] args) {
        IR_Scope scope = IR_Builder.buildFromMain(args);

        System.out.println("INTERMEDIATE REPRESENTATION:");
        System.out.println(scope);

        JVM jvm = new JVM();
        System.out.println("\nGENERATED BYTECODE:");
        jvm.codegen(scope);
    }
**/

    public JVM() {
    }

    public ClassVisitor cls() {
        return clsData().cls;
    }

    public ClassData clsData() {
        return clsStack.peek();
    }

    public void pushclass() {
        if (DEBUG) {
            PrintWriter pw = new PrintWriter(System.out);
            clsStack.push(new ClassData(new TraceClassVisitor(new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS), pw)));
            pw.flush();
        } else {
            clsStack.push(new ClassData(new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)));
        }
    }

    public void popclass() {
        clsStack.pop();
    }

    public GeneratorAdapter method() {
        return clsData().method();
    }

    public void pushmethod(String name) {
        clsData().pushmethod(name);
    }

    public void popmethod() {
        clsData().popmethod();
    }

    public void codegen(IRScope scope) {
        if (scope instanceof IRScript) {
            codegen((IRScript)scope);
        }
    }

    public void codegen(IRScript script) {
        this.script = script;
        emit(script.getRootClass());
    }

    public void emit(IRClass cls) {
        pushclass();
        cls().visit(RubyInstanceConfig.JAVA_VERSION, ACC_PUBLIC + ACC_SUPER, cls.getName(), null, p(RubyObject.class), null);
        cls().visitSource(script.getFileName().toString(), null);

        // root-level logic
        pushmethod("__class__");
        for (Instr instr: cls.getInstrs()) {
            emit(instr);
        }
        popmethod();

        // root-level methods
        for (IRMethod method : cls.getMethods()) {
            emit(method);
        }

        // root-level classes
        for (IRClass cls2 : cls.getClasses()) {
            emit(cls2);
        }

        cls().visitEnd();
        popclass();
    }

    public void emit(IRMethod method) {
        pushmethod(method.getName());

        for (Instr instr: method.getInstrs()) {
            emit(instr);
        }
        
        popmethod();
    }

    public void emit(Instr instr) {
        switch (instr.operation) {
        case BEQ:
            emitBEQ((BEQInstr)instr); break;
        case CALL:
            emitCALL((CallInstr) instr); break;
        case COPY:
            emitCOPY((CopyInstr)instr); break;
        case DEF_INST_METH:
            emitDEF_INST_METH((DefineInstanceMethodInstr)instr); break;
        case JUMP:
            emitJUMP((JumpInstr)instr); break;
        case LABEL:
            emitLABEL((LABEL_Instr)instr); break;
        case PUT_FIELD:
            emitPUT_FIELD((PutFieldInstr)instr); break;
        case GET_FIELD:
            emitGET_FIELD((GetFieldInstr)instr); break;
        case RECV_ARG:
            emitRECV_ARG((ReceiveArgumentInstruction)instr); break;
        case RETURN:
            emitRETURN((ReturnInstr) instr); break;
        default:
            System.err.println("unsupported: " + instr.operation);
        }
    }

    public void emit(Constant constant) {
        if (constant instanceof Fixnum) {
            method().push(((Fixnum)constant).value);
        }
    }

    public void emit(Operand operand) {
        if (operand.isConstant()) {
            emit((Constant)operand);
        } else if (operand instanceof Variable) {
            emit((Variable)operand);
        }
    }

    public void emit(Variable variable) {
        int index = getVariableIndex(variable);
        method().loadLocal(index);
    }

    public void emitBEQ(BEQInstr beq) {
        Operand[] args = beq.getOperands();
        emit(args[0]);
        emit(args[1]);
        method().ifCmp(Type.getType(Object.class), EQ, getLabel(beq.getJumpTarget()));
    }

    public void emitCOPY(CopyInstr copy) {
        int index = getVariableIndex(copy.result);
        emit(copy.getOperands()[0]);
        method().storeLocal(index);
    }

    public void emitCALL(CallInstr call) {
        emit(call.getReceiver());
        for (Operand operand : call.getCallArgs()) {
            emit(operand);
        }
        method().invokeVirtual(Type.getType(Object.class), Method.getMethod("Object " + call.getMethodAddr() + " ()"));
    }

    public void emitDEF_INST_METH(DefineInstanceMethodInstr instr) {
        IRMethod irMethod = instr.getMethod();
        GeneratorAdapter adapter = new GeneratorAdapter(ACC_PUBLIC, Method.getMethod("void " + irMethod.getName() + " ()"), null, null, cls());
        adapter.loadThis();
        adapter.loadArgs();
        adapter.invokeStatic(Type.getType(Object.class), Method.getMethod("Object __ruby__" + irMethod.getName() + " (Object)"));
        adapter.returnValue();
        adapter.endMethod();
    }

    public void emitDEF_CLS_METH(DefineClassMethodInstr instr) {
        IRMethod irMethod = instr.getMethod();
        GeneratorAdapter adapter = new GeneratorAdapter(ACC_PUBLIC | ACC_STATIC, Method.getMethod("void " + irMethod.getName() + " ()"), null, null, cls());
        adapter.returnValue();
        adapter.endMethod();
    }

    public void emitJUMP(JumpInstr jump) {
        method().goTo(getLabel(jump.target));
    }

    public void emitLABEL(LABEL_Instr lbl) {
        method().mark(getLabel(lbl._lbl));
    }

    public void emitPUT_FIELD(PutFieldInstr putField) {
        String field = ((FieldRef)putField.getOperands()[1]).getName();
        declareField(field);
        emit(putField.getOperands()[0]);
        emit(putField.getOperands()[2]);
        method().putField(Type.getType(Object.class), field, Type.getType(Object.class));
    }

    public void emitGET_FIELD(GetFieldInstr putField) {
        String field = ((FieldRef)putField.getOperands()[1]).getName();
        declareField(field);
        emit(putField.getOperands()[0]);
        method().getField(Type.getType(Object.class), field, Type.getType(Object.class));
    }

    public void emitRETURN(ReturnInstr ret) {
        emit(ret.getOperands()[0]);
        method().returnValue();
    }

    public void emitRECV_ARG(ReceiveArgumentInstruction recvArg) {
        int index = getVariableIndex(recvArg.result);
        // TODO: need to get this back into the method signature...now is too late...
    }

    private int getVariableIndex(Variable variable) {
        Integer index = clsStack.peek().methodStack.peek().varMap.get(variable);
        if (index == null) {
            index = method().newLocal(Type.getType(Object.class));
            clsStack.peek().methodStack.peek().varMap.put(variable, index);
        }
        return index;
    }

    private org.objectweb.asm.Label getLabel(Label label) {
        org.objectweb.asm.Label asmLabel = clsData().methodData().labelMap.get(label);
        if (asmLabel == null) {
            asmLabel = method().newLabel();
            clsData().methodData().labelMap.put(label, asmLabel);
        }
        return asmLabel;
    }

    private void declareField(String field) {
        if (!clsData().fieldSet.contains(field)) {
            cls().visitField(ACC_PROTECTED, field, ci(Object.class), null, null);
            clsData().fieldSet.add(field);
        }
    }
}
