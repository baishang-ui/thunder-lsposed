package io.github.baishangui.thunder;

/** Whole TextView regions are independent; text ticks cannot continuously hide a label. */
public final class CloudTextTransitionCheck {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void near(float actual,float expected,String message){check(Math.abs(actual-expected)<.00001f,message+": "+actual);}
    public static void main(String[] args) {
        CloudTextTransition title=new CloudTextTransition(),timer=new CloudTextTransition();
        check(title.update("Title",0,300,false),"initial title snapshot accepted");
        near(title.sample(0),1,"existing title starts sharp");
        check(!title.update(new String("Title"),10,300,true),"equal text on rebound view does not replay");
        timer.update("00:00",0,300,false);timer.update("00:01",1,300,false);
        near(timer.sample(1),0,"first real timer change starts locally even just after bind");
        near(title.sample(100),1,"changing timer leaves unchanged title sharp");
        float previous=0;
        for(int t=1;t<=301;t++) {
            float phase=timer.sample(t);check(phase>=previous,"region phase monotonic");previous=phase;
        }
        near(timer.sample(301),1,"region resolves sharp");check(!timer.running(301),"completed timer has no running clock");
        timer.update("00:02",1001,300,false);near(timer.sample(1001),0,"quiet one second tick starts fresh fade");
        float middle=timer.sample(1101);timer.update("00:03",1101,300,false);
        near(timer.sample(1101),middle,"update inside fade keeps current alpha");
        timer.update("00:04",1201,300,false);timer.update("00:05",1301,300,false);
        near(timer.sample(1301),1,"dense timer reaches sharp instead of restarting to zero");
        timer.update("00:06",1401,300,false);near(timer.sample(1401),1,"continuous burst remains visible after phase completion");
        timer.update("00:07",1801,300,false);near(timer.sample(1801),0,"burst ends after a full quiet duration");
        timer.finish();near(timer.sample(1801),1,"finish restores native sharp text");
        check(!timer.update("00:07",2000,300,false),"finish retains text deduplication");
        timer.reset();timer.update("new",3000,0,true);near(timer.sample(3000),1,"zero duration is sharp immediately");
        check(!timer.running(3000),"zero duration never schedules animation");
        timer.reset();timer.update("",4000,300,true);near(timer.sample(4000),1,"empty TextView needs no effect clock");
        timer.reset();timer.update("replacement",5000,300,true);near(timer.sample(5000),0,"new nonempty region can animate");
        timer.update("",5100,300,false);near(timer.sample(5100),1,"clearing region stops needless animation");
        System.out.println("Verified "+checks+" scoped text phase checks.");
    }
}
