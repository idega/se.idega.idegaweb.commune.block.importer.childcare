package se.idega.idegaweb.commune.block.importer.business;


public class NackaFSWOPlaceImportFileHandlerHomeImpl extends com.idega.business.IBOHomeImpl implements NackaFSWOPlaceImportFileHandlerHome
{
 protected Class getBeanInterfaceClass(){
  return NackaFSWOPlaceImportFileHandler.class;
 }


 public NackaFSWOPlaceImportFileHandler create() throws javax.ejb.CreateException{
  return (NackaFSWOPlaceImportFileHandler) super.createIBO();
 }



}