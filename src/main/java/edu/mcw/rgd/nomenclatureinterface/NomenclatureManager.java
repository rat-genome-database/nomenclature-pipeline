package edu.mcw.rgd.nomenclatureinterface;
import java.text.SimpleDateFormat;
import java.util.*;

import edu.mcw.rgd.dao.impl.NomenclatureDAO;
import edu.mcw.rgd.process.Utils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import edu.mcw.rgd.dao.impl.GeneDAO;
import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.SpeciesType;
import org.springframework.core.io.FileSystemResource;

/**
 * @author dli
 * <p>
 * Manager class used by the nomenclature interface
 * <p>
 * Much of this class was inherited from dli.  Updated by jdepons
 */
public class NomenclatureManager {

    String version;
    InformaticPrescreener informaticPrescreener;
    GeneDAO geneDAO = new GeneDAO();
    Logger log = Logger.getLogger("status");

    /**
     * Runs findGenesUpForReview().   Available from the shell.
     * @param args cmdline params (not used at the moment)
     * @throws Exception
     */
    public static void main(String[] args) throws Exception{

        try {
            DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
            new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));
            NomenclatureManager nomenclatureManager = (NomenclatureManager) (bf.getBean("nomenclatureManager"));
            nomenclatureManager.findGenesUpForReview();
        } catch( Exception e ) {
            Logger log = Logger.getLogger("status");
            Utils.printStackTrace(e, log);
            throw e;
        }
    }

    /**
     * Sets nomenclature of a gene as untouchable.
     * @param gene
     * @throws Exception
     */
    private void setUntouchable(Gene gene) throws Exception{
        gene.setNomenReviewDate(NomenclatureDAO.NOMENDATE_UNTOUCHABLE);
        geneDAO.updateGene(gene);
    }

    /**
     * Sets the genes nomenclature as reviewable.  It will be checked each time findGenesUpForReview() is run
     *
     * @param gene
     * @throws Exception
     */
    private void setReviewable(Gene gene) throws Exception{
        if (gene.getNomenReviewDate() == null ||(gene.getNomenReviewDate().getTime() != new GregorianCalendar(2100,9,1).getTime().getTime())) {
            gene.setNomenReviewDate(NomenclatureDAO.NOMENDATE_REVIEWABLE);
            geneDAO.updateGene(gene);
        }
    }

    /**
     * Checks all genes in Reviewable bucket and runs the proposed nomenclature algorithm.
     * If a gene is found that now has a proposed nomenclature the nomenclature review
     * date is changed to today
     *
     *
     * @throws Exception
     */
    public void findGenesUpForReview() throws Exception {

        Date date0 = new Date();
        log.info(getVersion());
        log.info("  "+geneDAO.getConnectionInfo());

        SimpleDateFormat sdt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        log.info("   started at "+sdt.format(date0));

        //get a list of active genes from RGD
        List<Gene> activeGenes = geneDAO.getActiveGenes(3, NomenclatureDAO.NOMENDATE_START,NomenclatureDAO.NOMENDATE_REVIEWABLE);

        int noGoodOrtholog = 0;
        int noChange = 0;
        int newNomen = 0;
        int untouchable = 0;

        for (Gene gene : activeGenes) {
            if (informaticPrescreener.isUntouchable(gene)) {
                setUntouchable(gene);
                untouchable++;
                continue;
            }

            List<Gene> orthologs = geneDAO.getActiveOrthologs(gene.getRgdId());
            if (orthologs == null || orthologs.size() == 0) {
                setReviewable(gene);
                noGoodOrtholog++;
                continue;
            }

            Gene proposedNomen = selectGoodOrtholog(orthologs);
            if (proposedNomen == null) {
                setReviewable(gene);
                noGoodOrtholog++;
            } else {
                // the name pass the informatic prescreening
                Gene newGeneNomen = proposeNewNomenClature(gene, proposedNomen);
                if (newGeneNomen == null) {
                    setReviewable(gene);
                    noChange++;
                } else {
                    if (newGeneNomen.getSymbol() == null || newGeneNomen.getSymbol().length() == 0) {
                        setReviewable(gene);
                        //noChangeC++;
                    } else {

                        // Added by WLiu on 4/12/2010
                        // Skip the rest if the gene is not reviewable by the pipeline.
                        if ( gene.getNomenReviewDate().getTime() != NomenclatureDAO.NOMENDATE_REVIEWABLE.getTime() ) {
                            noChange++;
                            continue;
                        }
                        // WLiu

                        gene.setNomenReviewDate(new Date());
                        geneDAO.updateGene(gene);
                        newNomen++;
                    }
                }
            }
        }

        log.info("===");
        log.info("  No Good Ortholog: " + noGoodOrtholog);
        log.info("  No Change: " + noChange);
        log.info("  New Nomenclature: " + newNomen);
        log.info("  Untouchable: " + untouchable);
        log.info("=== Pipeline finished;  elapsed " + Utils.formatElapsedTime(date0.getTime(), System.currentTimeMillis()));
        log.info(" ");
    }

    /**
     * Runs the proposal algorithm.  Returns a Gene object containing the proposed nomenclature.
     * @param ratGene rat Gene object
     * @param orthologGene ortholog Gene object
     * @return Gene object containing the proposed nomenclature
     */
    public  Gene proposeNewNomenClature (Gene ratGene, Gene orthologGene) {

        if (orthologGene == null) return null;

        Gene newGeneNomen=null;
        String ratGeneSymbol=ratGene.getSymbol();
        String orthologGeneSymbol=orthologGene.getSymbol();
        if (ratGeneSymbol==null || orthologGeneSymbol==null)
            return newGeneNomen;

        // remove _predicted, _mapped from gene symbol
        ratGeneSymbol=ratGeneSymbol.replaceAll("_predicted", "");
        orthologGeneSymbol=orthologGeneSymbol.replaceAll("_predicted", "");
        ratGeneSymbol=ratGeneSymbol.replaceAll("_mapped", "");
        orthologGeneSymbol=orthologGeneSymbol.replaceAll("_mapped", "");

        //comparing gene symbol, the gene symbol equals, check the gene name
        if (ratGeneSymbol.equalsIgnoreCase(orthologGeneSymbol)) {
            String ratGeneName=ratGene.getName();
            String orthologGeneName=orthologGene.getName();
            if (ratGeneName==null || orthologGeneName ==null)
                return newGeneNomen;

            // before comparing name, remove (predicted) ......
            ratGeneName=ratGeneName.replaceAll("(predicted)", "");
            ratGeneName=ratGeneName.replaceAll("(mapped)", "");
            orthologGeneName=orthologGeneName.replaceAll("(predicted)", "");
            orthologGeneName=orthologGeneName.replaceAll("(mapped)", "");
            ratGeneName=ratGeneName.replaceAll("[ !@#\\$%\\^&\\*\\(\\)_\\+\\-={}\\|:\"<>\\?\\-=\\[\\];',\\./`~']","").toLowerCase();
            orthologGeneName=orthologGeneName.replaceAll("[ !@#\\$%\\^&\\*\\(\\)_\\+\\-={}\\|:\"<>\\?\\-=\\[\\];',\\./`~']","").toLowerCase();

            // the gene symbol equals, name not equal
            if (!ratGeneName.equals(orthologGeneName)) {
                // get the proposed new gene symbol
                String proposedGeneSymbol=orthologGene.getSymbol();
                if (ratGene.getSymbol().endsWith("_predicted") ) {
                    proposedGeneSymbol=proposedGeneSymbol+"_predicted";
                } else if (ratGene.getSymbol().endsWith("_mapped")) {
                    proposedGeneSymbol=proposedGeneSymbol+"_mapped";
                }
                proposedGeneSymbol=proposedGeneSymbol.substring(0,1).toUpperCase() + proposedGeneSymbol.substring(1).toLowerCase();
                // get the proposed new gene name
                String proposedGeneName=orthologGene.getName();
                if (ratGene.getName().endsWith("(predicted)") ) {
                    proposedGeneName=proposedGeneName+" (predicted)";
                } else if (ratGene.getName().endsWith("(mapped)")) {
                    proposedGeneName=proposedGeneName+" (mapped)";
                }

                //process.info("GeneName: "+ ratGeneName+"\t"+orthologGeneName);
                newGeneNomen=new Gene();
                newGeneNomen.setName(proposedGeneName);
                newGeneNomen.setSymbol(proposedGeneSymbol);
                newGeneNomen.setKey(orthologGene.getKey());
                newGeneNomen.setRgdId(orthologGene.getRgdId());
            }
            return newGeneNomen;
        }

        if (!ratGeneSymbol.equalsIgnoreCase(orthologGeneSymbol)) {
            // get the proposed new gene symbol
            String proposedGeneSymbol = orthologGene.getSymbol();
            if (ratGene.getSymbol().endsWith("_predicted") ) {
                proposedGeneSymbol += "_predicted";
            } else if (ratGene.getSymbol().endsWith("_mapped")) {
                proposedGeneSymbol += "_mapped";
            }
            proposedGeneSymbol=proposedGeneSymbol.substring(0,1).toUpperCase() + proposedGeneSymbol.substring(1).toLowerCase();

            // get the proposed new gene name
            String proposedGeneName = orthologGene.getName();
            String ratGeneName = Utils.defaultString(ratGene.getName());
            if (ratGeneName.endsWith("(predicted)") ) {
                proposedGeneName += " (predicted)";
            } else if (ratGeneName.endsWith("(mapped)")) {
                proposedGeneName += " (mapped)";
            }

            newGeneNomen=new Gene();
            newGeneNomen.setName(proposedGeneName);
            newGeneNomen.setSymbol(proposedGeneSymbol);
            newGeneNomen.setKey(orthologGene.getKey());
            newGeneNomen.setRgdId(orthologGene.getRgdId());
        }
        return newGeneNomen;
    }

    public Gene selectGoodOrtholog(List<Gene> orthologs) {
        List<Gene> mouseOrthologs=getOrthologNomenclatureBySpecies(orthologs, SpeciesType.HUMAN);

        // looking for new nomen from mouse nomen
        if (mouseOrthologs !=null && mouseOrthologs.size() >0) {
            // use mouse orthologs
            for (Gene gene : mouseOrthologs) {
                if (informaticPrescreener.validSymbol(gene.getSymbol()) &&
                        informaticPrescreener.validName(gene.getName())) {
                    // the symbol and name pass the informatic prescreening
                    return gene;
                }
            }
        }

        // looking for new nomenclature from human

        List<Gene> humanOrthologs=getOrthologNomenclatureBySpecies(orthologs, SpeciesType.MOUSE);
        //process.info("Checking gene name and gene symbol: " + humanOrthologs.size());
        for (Gene gene : humanOrthologs) {

            if (informaticPrescreener.validSymbol(gene.getSymbol()) &&
                    informaticPrescreener.validName(gene.getName())) {
                // the symbol and name is valid
                return gene;
            }
        }
        return null;
    }

    public List<Gene> getOrthologNomenclatureBySpecies(List<Gene> orthologs, int speciesKey) {
        List<Gene> matchingOrthologs=new ArrayList<Gene>();
        if (orthologs==null)
            return matchingOrthologs;
        for (Gene gene : orthologs) {
            if (gene.getSpeciesTypeKey() == speciesKey) {
                matchingOrthologs.add(gene);
            }
        }
        return matchingOrthologs;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public InformaticPrescreener getInformaticPrescreener() {
        return informaticPrescreener;
    }
    public void setInformaticPrescreener(
            InformaticPrescreener informaticPrescreener) {
        this.informaticPrescreener = informaticPrescreener;
    }

}
