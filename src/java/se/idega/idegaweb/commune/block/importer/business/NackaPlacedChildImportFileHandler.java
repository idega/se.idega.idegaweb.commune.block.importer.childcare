package se.idega.idegaweb.commune.block.importer.business;


public interface NackaPlacedChildImportFileHandler extends com.idega.business.IBOService,com.idega.block.importer.business.ImportFileHandler
{
 public java.util.List getFailedRecords() throws java.rmi.RemoteException;
 public boolean handleRecords()throws java.rmi.RemoteException, java.rmi.RemoteException;
 public void printFailedRecords() throws java.rmi.RemoteException;
 public void setImportFile(com.idega.block.importer.data.ImportFile p0) throws java.rmi.RemoteException;
 public void setRootGroup(com.idega.user.data.Group p0) throws java.rmi.RemoteException;
}
