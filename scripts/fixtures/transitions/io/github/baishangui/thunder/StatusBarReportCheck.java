package io.github.baishangui.thunder;
import android.content.ContentResolver;
import android.os.Handler;
import com.android.server.wm.WindowManagerService;

/** Exercise the actual asynchronous report path, including silent Binder failure. */
public class StatusBarReportCheck {
 public static void main(String[] args)throws Throwable {
  StatusBarFollowHook.install(StatusBarReportCheck.class.getClassLoader());
  StatusBarFollowCheck.wm=new WindowManagerService();
  StatusBarFollowCheck.call(false,"WindowManagerService","<init>",StatusBarFollowCheck.wm);
  var field=StatusBarFollowHook.class.getDeclaredField("worker");field.setAccessible(true);
  Handler worker=(Handler)field.get(null);
  var taskField=StatusBarFollowHook.class.getDeclaredField("reportTask");taskField.setAccessible(true);
  Runnable reportTask=(Runnable)taskField.get(null);
  StatusBarFollowCheck.config(true,1);
  if(args[0].equals("coalesce")) {
   int scheduled=worker.delayedSchedules;
   StatusBarFollowCheck.config(true,2);StatusBarFollowCheck.config(true,3);
   StatusBarFollowCheck.check(worker.delayedSchedules==scheduled,"updates do not postpone an already scheduled report");
   worker.drainDelayed();
   StatusBarFollowCheck.check(ContentResolver.calls==1 && ContentResolver.last.getLong("revision",-1)==3,"coalesced report sends the newest configuration");
  } else if(args[0].equals("retry")) {
   ContentResolver.response=null;worker.drainDelayed();
   StatusBarFollowCheck.check(worker.hasCallbacks(reportTask),"null provider acknowledgement schedules a bounded retry");
   ContentResolver.response=android.os.Bundle.EMPTY;worker.drainDelayed();
   StatusBarFollowCheck.check(ContentResolver.calls==2 && !worker.hasCallbacks(reportTask),"provider recovery delivers the retained report and stops retrying");
  } else {
   ContentResolver.response=null;
   for(int i=0;i<8;i++)worker.drainDelayed();
   StatusBarFollowCheck.check(ContentResolver.calls==8 && !worker.hasCallbacks(reportTask),"persistent provider failure stops automatic retry after eight attempts");
  }
 }
}
