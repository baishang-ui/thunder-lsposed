package io.github.baishangui.thunder;

/** Character clocks are independent from whole-scene reveals. */
public final class CloudTextTransitionCheck {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void near(float actual,float expected,String message){check(Math.abs(actual-expected)<.00001f,message+": "+actual);}
    public static void main(String[] args) {
        CloudTextTransition text=new CloudTextTransition();
        text.update("00:09",0,300,false);text.update("00:10",10,300,false);
        check(text.count()==5,"timer has five character states");
        for(int i=0;i<3;i++)near(text.sample(i,10),1,"unchanged timer prefix remains sharp");
        near(text.sample(3,10),0,"changed tens starts blurred");near(text.sample(4,10),0,"changed units starts blurred");
        check(text.roll(3,10)>0,"changed character enters from below");
        float tens=text.sample(3,110);text.update("00:11",110,300,false);
        near(text.sample(3,110),tens,"unchanged in-flight character retains its own clock");
        near(text.sample(4,110),0,"new units change starts its own transition during another change");
        near(text.sample(3,310),1,"earlier character finishes independently");
        check(text.sample(4,310)<1,"later character still runs");
        check(!text.update("00:11",320,300,true),"equal text cannot rewind clocks");
        near(text.sample(4,410),1,"later character resolves");check(!text.running(410),"all character clocks settle");
        text.update("等待中",1000,300,false);text.finish();text.update("等待完成",1100,300,false);
        near(text.sample(0,1100),1,"unchanged Chinese character remains sharp");near(text.sample(1,1100),1,"second unchanged Chinese character remains sharp");
        near(text.sample(2,1100),0,"replacement Chinese character animates");near(text.sample(3,1100),0,"inserted Chinese character animates");
        text.finish();text.update("🇨🇳🇺🇸",1900,300,false);check(text.count()==2,"adjacent flags never merge into one animation unit");
        text.finish();text.update("A👩‍💻é🇨🇳Z",2000,300,false);
        check(text.count()==5,"emoji ZWJ, combining accent and flag remain complete graphemes");
        text.finish();text.update("A👨‍💻é🇨🇳Z",2100,300,false);
        near(text.sample(0,2100),1,"emoji change leaves prefix stable");near(text.sample(1,2100),0,"changed emoji is one animation unit");near(text.sample(2,2100),1,"accent unchanged after emoji");
        text.reveal(2200,300);for(int i=0;i<text.count();i++){near(text.sample(i,2200),0,"scene switch reveals every character even at same value");near(text.roll(i,2200),0,"scene reveal does not roll");}
        text.update("B👨‍💻é🇨🇳Z",2250,300,false);near(text.sample(0,2250),text.sample(1,2250),"text updates during scene reveal use its common clock");
        near(text.roll(0,2250),0,"scene update cannot introduce local roll");near(text.sample(0,2500),1,"scene clock is not restarted by a text tick");
        text.update("B👨‍💻é🇨🇳Y",2510,300,false);near(text.roll(4,2510),1,"local changes roll again after scene completes");
        text.finish();check(!text.running(2510),"finish restores all characters");
        text.reset();text.update("zero",3000,0,true);check(!text.running(3000),"zero duration stays native");
        text.reset();text.update("",4000,300,true);check(!text.running(4000),"empty label needs no animation");
        text.update("abcXYZ",5000,300,false);text.finish();text.update("abc!XYZ",5100,300,false);
        near(text.sample(3,5100),0,"only inserted character animates");near(text.sample(4,5100),1,"shifted unchanged suffix remains sharp");
        text.reset();text.update("新增",6000,300,true);
        near(text.roll(0,6000),1,"a new label inside the current scene rolls in too");
        text.reveal(6000,300);near(text.roll(0,6000),0,"scene reveal overrides a new label's local roll");
        System.out.println("Verified "+checks+" character/scene text phase checks.");
    }
}
