package se.idega.idegaweb.commune.block.importer.business;


public class NackaQueueImportFileHandlerHomeImpl extends com.idega.business.IBOHomeImpl implements NackaQueueImportFileHandlerHome
{
 protected Class getBeanInterfaceClass(){
  return NackaQueueImportFileHandler.class;
 }


 public NackaQueueImportFileHandler create() throws javax.ejb.CreateException{
  return (NackaQueueImportFileHandler) super.createIBO();
 }



}