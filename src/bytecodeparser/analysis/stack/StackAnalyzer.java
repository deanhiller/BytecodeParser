/**
 * 
 */
package bytecodeparser.analysis.stack;

import java.util.Arrays;
import java.util.Iterator;

import javassist.CtBehavior;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import bytecodeparser.CodeParser;
import bytecodeparser.Context;
import bytecodeparser.OpHandler;
import bytecodeparser.analysis.Opcodes;
import bytecodeparser.analysis.decoders.DecodedBranchOp;
import bytecodeparser.analysis.decoders.DecodedMethodInvocationOp;
import bytecodeparser.analysis.decoders.DecodedOp;
import bytecodeparser.analysis.decoders.DecodedSwitchOpcode;
import bytecodeparser.analysis.opcodes.BranchOpCode;
import bytecodeparser.analysis.opcodes.ExitOpcode;
import bytecodeparser.analysis.opcodes.Op;
import bytecodeparser.analysis.opcodes.SwitchOpcode;
import bytecodeparser.analysis.stack.Stack.StackElementLength;

public class StackAnalyzer {
	public final Context context;
	final Stack stack;
	final Frame[] frames;
	
	public StackAnalyzer(CtBehavior behavior) {
		this.context = new Context(behavior);
		this.stack = new Stack();
		this.frames = new Frame[context.behavior.getMethodInfo().getCodeAttribute().getCodeLength()];
	}
	
	public Frames analyze() throws BadBytecode {
		if(frames[0] == null) {
			init();
			analyze(0, new Stack());
			parseCatchBlocks();
		}
		return new Frames(context.behavior, frames);
	}
	
	void init() throws BadBytecode {
		new CodeParser(context.behavior).parse(new OpHandler() {
			@Override
			public void handle(Op op, int index) {
				Frame frame = frames[index] = new Frame();
				frame.index = index;
			}
		});
	}
	
	void parseCatchBlocks() throws BadBytecode {
		for(int index : context.exceptionHandlers) {
			//System.out.println("parse catch block " + index + ", " + frames[index]);
			analyze(index, new Stack().push(new Whatever()));
		}
	}
	
	void analyze(int from, Stack stack) throws BadBytecode {
		//System.out.println("parse from " + from + " with stack " + stack);
		StringBuffer onError = new StringBuffer();
		try {
			if(frames[from].isAccessible) // already parsed
				return;
			CodeIterator iterator = context.behavior.getMethodInfo().getCodeAttribute().iterator();
			iterator.move(from);
			Stack currentStack = stack.copy();
			while(iterator.hasNext()) {
				int index = iterator.next();
				Op op = Opcodes.OPCODES.get(iterator.byteAt(index)).init(context, index);
				onError.append("\n").append(index).append(":").append(op.getName()).append(" --> ");
				Frame frame = frames[index];
				frame.isAccessible = true;
				frame.stackBefore = currentStack.copy();
				frame.decodedOp = op.decode(context, index);
				if(frame.decodedOp instanceof DecodedBranchOp)
					onError.append(" [jump to ").append(((DecodedBranchOp)frame.decodedOp).getJump()).append("] ");
				if(frame.decodedOp instanceof DecodedMethodInvocationOp)
					onError.append(" [params = ").append(StackElementLength.add(((DecodedMethodInvocationOp)frame.decodedOp).getPops())).append(" -> ").append(Arrays.toString(((DecodedMethodInvocationOp)frame.decodedOp).getParameterTypes())).append("] ");
				frame.decodedOp.simulate(currentStack);
				frame.stackAfter = currentStack.copy();
				onError.append(frame.stackAfter);
				
				if( !(op instanceof ExitOpcode || (op instanceof BranchOpCode && !((BranchOpCode)op).isConditional()) || op instanceof SwitchOpcode) )
					onError.append(". Next is ").append(iterator.lookAhead());
				
				if(op instanceof ExitOpcode)
					return;
				
				if(op instanceof BranchOpCode) {
					BranchOpCode branchOpCode = op.as(BranchOpCode.class);
					int jump = branchOpCode.decode(context, index).getJump();
					analyze(jump, frame.stackAfter);
					if(!branchOpCode.isConditional())
						return;
				}
				
				if(op instanceof SwitchOpcode) {
					SwitchOpcode switchOpcode = op.as(SwitchOpcode.class);
					DecodedSwitchOpcode decodedSwitchOpcode = switchOpcode.decode(context, index);
					//System.out.println(decodedSwitchOpcode.toString());
					for(int offset : decodedSwitchOpcode.offsets)
						analyze(offset, frame.stackAfter);
					analyze(decodedSwitchOpcode.defaultOffset, frame.stackAfter);
					return;
				}
			}
		} catch (Exception e) {
			System.out.println("BCLIBS ERROR !! " + onError.toString());
			throw new RuntimeException(e);
		}
	}
	
	public static class Frame {
		public Stack stackBefore;
		public Stack stackAfter;
		public int index;
		public DecodedOp decodedOp;
		public boolean isAccessible = false;
		
		@Override
		public String toString() {
			return "Frame " + index + " (" + decodedOp.op.getName() + "):" + stackBefore + " -> " + stackAfter + " " + (isAccessible ? "" : " NOT ACCESSIBLE");
		}
	}
	
	public static class Frames implements Iterable<Frame> {
        public final Frame[] frames;
        public final CtBehavior behavior;
        
        
        public Frames(CtBehavior behavior, Frame[] frames) {
            this.frames = frames;
            this.behavior = behavior;
        }
        
        @Override
        public FrameIterator iterator() {
            return new FrameIterator();
        }
        
        public class FrameIterator implements Iterator<Frame> {
            private int i = -1;
            private FrameCodeIterator iterator = new FrameCodeIterator(behavior.getMethodInfo().getCodeAttribute(), frames);
            
            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
            
            @Override
            public Frame next() {
                int nextIndex = nextIndex();
                if(nextIndex > -1) {
                    i = nextIndex;
                    return frames[nextIndex];
                }
                throw new IllegalStateException();
            }
            
            @Override
            public boolean hasNext() {
                return nextIndex() > -1;
            }
            
            public boolean isFirst() {
            	return i == 0;
            }
            
            public boolean isLast() {
            	return !hasNext();
            }
            
            public Frame lookAhead() {
                if(nextIndex() != -1)
                    return frames[nextIndex()];
                return null;
            }
            
            private int nextIndex() {
                for(int j = i + 1; j < frames.length; j++)
                    if(frames[j] != null)
                        return j;
                return -1;
            }
            
            public void insert(byte[] code, boolean after) throws BadBytecode {
                //System.out.println("insert bc " + code.length);
                int index = 0;
                if(!after && i != -1)
                    index = frames[i].index; 
                if(after && lookAhead() != null)
                    index = lookAhead().index;
                iterator.move(index);
                iterator.insert(code);
            }
        }
        
        static class FrameCodeIterator extends CodeIterator {
            final Frame[] frames;
            public FrameCodeIterator(CodeAttribute codeAttribute, Frame[] frames) {
                super(codeAttribute);
                this.frames = frames;
            }
            
            @Override
            protected void updateCursors(int pos, int length) {
                //System.out.println("updateCursors: gap of length " + length + " inserted at " + pos);
                super.updateCursors(pos, length);
                for(Frame frame : frames) {
                    if(frame != null && frame.index > pos)
                        frame.index += length;
                }
            }
        }
    }
}