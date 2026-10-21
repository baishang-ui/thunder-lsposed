package io.github.baishangui.thunder;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Function;

/** RecyclerView wrappers can survive a removal while their bound content changes. */
final class CloudCardRoster<T> {
    record Change<T>(List<T> ordered,List<T> removed,List<T> rebound,boolean orderChanged) {
        boolean changed(){return orderChanged||!rebound.isEmpty();}
    }
    private List<T> order=List.of();
    private IdentityHashMap<T,Object> content=new IdentityHashMap<>();
    Change<T> sync(List<T> nativeOrder,Function<T,Object> source) {
        ArrayList<T> next=new ArrayList<>(),removed=new ArrayList<>(),rebound=new ArrayList<>();
        IdentityHashMap<T,Object> nextContent=new IdentityHashMap<>();
        for(T item:nativeOrder)if(item!=null&&!nextContent.containsKey(item)) {
            Object bound=source.apply(item);next.add(item);nextContent.put(item,bound);
            if(!content.containsKey(item)||content.get(item)!=bound)rebound.add(item);
        }
        for(T item:order)if(!nextContent.containsKey(item))removed.add(item);
        boolean reordered=order.size()!=next.size();
        if(!reordered)for(int i=0;i<order.size();i++)if(order.get(i)!=next.get(i)){reordered=true;break;}
        order=List.copyOf(next);content=nextContent;
        return new Change<>(order,List.copyOf(removed),List.copyOf(rebound),reordered);
    }
}
