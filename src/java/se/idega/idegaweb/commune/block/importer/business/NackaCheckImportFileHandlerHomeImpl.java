package se.idega.idegaweb.commune.block.importer.business;


public class NackaCheckImportFileHandlerHomeImpl extends com.idega.business.IBOHomeImpl implements NackaCheckImportFileHandlerHome
{
 protected Class getBeanInterfaceClass(){
  return NackaCheckImportFileHandler.class;
 }


 public NackaCheckImportFileHandler create() throws javax.ejb.CreateException{
  return (NackaCheckImportFileHandler) super.createIBO();
 }



}