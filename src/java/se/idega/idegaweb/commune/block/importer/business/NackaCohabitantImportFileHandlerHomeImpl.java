package se.idega.idegaweb.commune.block.importer.business;


public class NackaCohabitantImportFileHandlerHomeImpl extends com.idega.business.IBOHomeImpl implements NackaCohabitantImportFileHandlerHome
{
 protected Class getBeanInterfaceClass(){
  return NackaCohabitantImportFileHandler.class;
 }


 public NackaCohabitantImportFileHandler create() throws javax.ejb.CreateException{
  return (NackaCohabitantImportFileHandler) super.createIBO();
 }



}