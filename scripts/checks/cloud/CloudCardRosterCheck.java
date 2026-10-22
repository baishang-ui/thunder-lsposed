package io.github.baishangui.thunder;

import java.util.IdentityHashMap;
import java.util.List;

/** Regression for native DiffUtil deletion, reorder and recycled wrapper binding. */
public final class CloudCardRosterCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        CloudCardRoster<Object> roster=new CloudCardRoster<>();
        Object a=new Object(),b=new Object(),c=new Object();
        IdentityHashMap<Object,Object> content=new IdentityHashMap<>();
        content.put(a,new Object());content.put(b,new Object());content.put(c,new Object());
        roster.sync(List.of(a,b,c),content::get);
        var middle=roster.sync(List.of(a,c),content::get);
        check(middle.ordered().equals(List.of(a,c))&&middle.removed().equals(List.of(b)),"middle removal keeps remaining native order");
        var first=roster.sync(List.of(c),content::get);
        check(first.ordered().get(0)==c&&first.removed().get(0)==a,"first removal promotes surviving card identity");
        check(!roster.sync(List.of(c),content::get).changed(),"stable native traversal does not rebind or cancel input");
        var stable=roster.sync(List.of(c),content::get);
        for(int i=0;i<1000;i++)check(roster.sync(List.of(c),content::get)==stable,
            "unchanged frames reuse the immutable roster result instead of rebuilding lists/maps");
        content.put(c,new Object());
        check(roster.sync(List.of(c),content::get).rebound().equals(List.of(c)),"recycled wrapper must refresh its content even with identical card identity");
        roster.sync(List.of(a,c,b),content::get);
        var reorder=roster.sync(List.of(b,c,a),content::get);
        check(reorder.orderChanged()&&reorder.ordered().get(0)==b,"native reorder updates frontmost identity");
        check(roster.sync(List.of(),content::get).removed().size()==3,"last removal releases all stale layers");
        var duplicate=roster.sync(java.util.Arrays.asList(a,null,a,b),content::get);
        check(duplicate.ordered().size()==2&&duplicate.ordered().get(0)==a&&duplicate.ordered().get(1)==b,
            "slow path still normalizes null and duplicate wrappers");
        var normalized=roster.sync(List.of(a,b),content::get);
        check(!normalized.changed()&&roster.sync(List.of(a,b),content::get)==normalized,
            "normalized order enters the stable fast path");
        content.put(a,null);
        check(roster.sync(List.of(a,b),content::get).rebound().equals(List.of(a)),"a cleared binding is still a content change");
        System.out.println("CloudCardRosterCheck: deletion, reorder, recycle and stable traversal checks passed");
    }
}
