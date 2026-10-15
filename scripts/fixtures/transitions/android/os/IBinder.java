package android.os;
public interface IBinder {int FIRST_CALL_TRANSACTION=1;interface DeathRecipient{void binderDied();}boolean transact(int code,Parcel data,Parcel reply,int flags)throws RemoteException;boolean isBinderAlive();void linkToDeath(DeathRecipient death,int flags)throws RemoteException;}
