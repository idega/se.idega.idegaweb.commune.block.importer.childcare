package se.idega.idegaweb.commune.block.importer.business;


public class NackaDBVWPlaceImportFileHandlerHomeImpl extends com.idega.business.IBOHomeImpl implements NackaDBVWPlaceImportFileHandlerHome
{
 protected Class getBeanInterfaceClass(){
  return NackaDBVWPlaceImportFileHandler.class;
 }


 public NackaDBVWPlaceImportFileHandler create() throws javax.ejb.CreateException{
  return (NackaDBVWPlaceImportFileHandler) super.createIBO();
 }



}