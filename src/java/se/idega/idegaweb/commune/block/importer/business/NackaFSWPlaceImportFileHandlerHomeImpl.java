package se.idega.idegaweb.commune.block.importer.business;


public class NackaFSWPlaceImportFileHandlerHomeImpl extends com.idega.business.IBOHomeImpl implements NackaFSWPlaceImportFileHandlerHome
{
 protected Class getBeanInterfaceClass(){
  return NackaFSWPlaceImportFileHandler.class;
 }


 public NackaFSWPlaceImportFileHandler create() throws javax.ejb.CreateException{
  return (NackaFSWPlaceImportFileHandler) super.createIBO();
 }



}