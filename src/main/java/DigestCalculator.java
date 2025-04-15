import java.security.*;
import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.*;
import org.xml.sax.*;

/**
 *  INTEGRANTES DA DUPLA:
 *  Lucas Caputo Bello - 1911432
 *  Sérgio Bernardelli Netto - 1911466
 */


/**
 * Classe principal para cálculo e verificação de digests de arquivos.
 * Compara digests calculados com uma lista conhecida em formato XML.
 */
public class DigestCalculator {


    private static final String ERROR_MESSAGE_ARGS = "Uso: DigestCalculator <Tipo_Digest> <Caminho_da_Pasta> <Caminho_ArqListaDigest>";

    private static final String ERROR_LOAD_XML = "Erro ao carregar o arquivo XML. Criando novo documento.";

    private static final String MESSAGE_DIGEST_ALGORITHMS_TIP = "Tipos de digest suportados: MD5, SHA1, SHA256, SHA512";

    private static final String ERROR_DIGEST_TYPE = "Tipo de digest não suportado: ";


    /**
     * Método principal que inicia a execução do programa.
     *
     * @param args Argumentos da linha de comando: Tipo_Digest, Caminho_da_Pasta, Caminho_ArqListaDigest
     */
    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println(ERROR_MESSAGE_ARGS);
            System.out.println(MESSAGE_DIGEST_ALGORITHMS_TIP);
            System.exit(1);
        }

        String digestType = args[0].toUpperCase();
        String folderPath = args[1];
        String digestListPath = args[2];

        try {
            if (!isDigestSupported(digestType)) {
                System.out.println(ERROR_DIGEST_TYPE + digestType);
                System.exit(1);
            }

            Document digestList = loadDigestList(digestListPath);
            Map<String, Map<String, String>> digestsListMap = buildDigestsListMap(digestList);

            File folder = new File(folderPath);
            File[] files = folder.listFiles();

            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        processFile(file, digestType, digestList, digestsListMap);
                    }
                }
            }

            saveDigestList(digestList, digestListPath);

        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Verifica se o tipo de digest é suportado pelo programa.
     *
     * @param digestType Tipo de digest a ser verificado.
     * @return true se o tipo for suportado, false caso contrário.
     */
    private static boolean isDigestSupported(String digestType) {
        return digestType.equals("MD5") || digestType.equals("SHA1") ||
                digestType.equals("SHA256") || digestType.equals("SHA512");
    }

    /**
     * Constrói um mapa de digests por arquivo a partir do documento XML.
     *
     * @param doc Documento XML contendo os digests conhecidos.
     * @return Mapa onde a chave é o nome do arquivo e o valor é outro mapa com tipos e valores de digest.
     */
    private static Map<String, Map<String, String>> buildDigestsListMap(Document doc) {
        Map<String, Map<String, String>> map = new HashMap<>();
        NodeList fileEntries = doc.getElementsByTagName("FILE_ENTRY");

        for (int i = 0; i < fileEntries.getLength(); i++) {
            Element fileEntry = (Element) fileEntries.item(i);
            String fileName = fileEntry.getElementsByTagName("FILE_NAME").item(0).getTextContent();

            Map<String, String> digests = new HashMap<>();
            NodeList digestEntries = fileEntry.getElementsByTagName("DIGEST_ENTRY");

            for (int j = 0; j < digestEntries.getLength(); j++) {
                Element digestEntry = (Element) digestEntries.item(j);
                String type = digestEntry.getElementsByTagName("DIGEST_TYPE").item(0).getTextContent();
                String hex = digestEntry.getElementsByTagName("DIGEST_HEX").item(0).getTextContent().trim();
                digests.put(type, hex);
            }

            map.put(fileName, digests);
        }

        return map;
    }

    /**
     * Processa um arquivo individual, calculando seu digest e verificando seu status.
     *
     * @param file Arquivo a ser processado.
     * @param digestType Tipo de digest a ser calculado.
     * @param digestList Documento XML com a lista de digests conhecidos.
     * @param digestsListMap Mapa de digests por arquivo.
     * @throws Exception Se ocorrer erro durante o processamento.
     */
    private static void processFile(File file, String digestType, Document digestList,
                                    Map<String, Map<String, String>> digestsListMap) throws Exception {
        String fileName = file.getName();
        String calculatedDigest = calculateFileDigest(file, digestType);

        String status = determineStatus(fileName, digestType, calculatedDigest, digestsListMap);

        System.out.println(fileName + " " + digestType + " " + calculatedDigest + " " + status);

        if (status.equals("NOT FOUND")) {
            addDigestToList(fileName, digestType, calculatedDigest, digestList, digestsListMap);
        }
    }

    /**
     * Determina o status de um arquivo com base no digest calculado.
     *
     * @param fileName Nome do arquivo sendo verificado.
     * @param digestType Tipo de digest sendo verificado.
     * @param calculatedDigest Valor do digest calculado.
     * @param digestsListMap Mapa de digests por arquivo.
     * @return String representando o status: "OK", "NOT OK", "NOT FOUND" ou "COLLISION".
     */
    private static String determineStatus(String fileName, String digestType, String calculatedDigest,
                                          Map<String, Map<String, String>> digestsListMap) {
        // Verificar colisão
        if (hasCollision(fileName, calculatedDigest, digestsListMap)) {
            return "COLLISION";
        }

        // Verificar se o arquivo está na lista
        if (digestsListMap.containsKey(fileName)) {
            Map<String, String> fileDigests = digestsListMap.get(fileName);

            if (fileDigests.containsKey(digestType)) {
                return fileDigests.get(digestType).equals(calculatedDigest) ? "OK" : "NOT OK";
            }
        }

        return "NOT FOUND";
    }

    /**
     * Verifica se há colisão de digest com outros arquivos.
     *
     * @param fileName Nome do arquivo sendo verificado.
     * @param digest Digest sendo verificado.
     * @param digestsListMap Mapa de digests por arquivo.
     */
    private static boolean hasCollision(String fileName, String digest,
                                        Map<String, Map<String, String>> digestsListMap) {
        for (Map.Entry<String, Map<String, String>> entry : digestsListMap.entrySet()) {
            if (entry.getKey().equals(fileName)) continue;

            if (entry.getValue().containsValue(digest)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calcula o digest de um arquivo usando o algoritmo especificado.
     *
     * @param file Arquivo para cálculo do digest.
     * @param digestType Tipo de digest a ser calculado (MD5, SHA1, etc.).
     * @return String hexadecimal representando o digest calculado.
     * @throws Exception Se ocorrer erro durante o cálculo.
     */
    private static String calculateFileDigest(File file, String digestType) throws Exception {
        MessageDigest md = MessageDigest.getInstance(digestType);

        try (InputStream is = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(is)) {

            byte[] buffer = new byte[512];
            int count;

            while ((count = bis.read(buffer)) > 0) {
                md.update(buffer, 0, count);
            }
        }

        byte[] digest = md.digest();
        return bytesToHex(digest);
    }

    /**
     * Converte um array de bytes em uma string hexadecimal.
     *
     * @param bytes Array de bytes a ser convertido.
     * @return String hexadecimal representando os bytes.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * Carrega a lista de digests a partir de um arquivo XML.
     *
     * @param path Caminho para o arquivo XML.
     * @return Documento XML carregado.
     * @throws Exception Se ocorrer erro durante o carregamento.
     */
    private static Document loadDigestList(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        try {
            File file = new File(path);
            if (file.exists() && file.length() > 0) {
                return builder.parse(file);
            } else {
                Document doc = builder.newDocument();
                Element root = doc.createElement("CATALOG");
                doc.appendChild(root);
                return doc;
            }
        } catch (SAXException | IOException e) {
            System.err.println(ERROR_LOAD_XML);
            Document doc = builder.newDocument();
            Element root = doc.createElement("CATALOG");
            doc.appendChild(root);
            return doc;
        }
    }

    /**
     * Adiciona um novo digest à lista XML, se não existir.
     *
     * @param fileName Nome do arquivo a ser adicionado.
     * @param digestType Tipo de digest a ser adicionado.
     * @param digestHex Valor hexadecimal do digest.
     * @param doc Documento XML onde será adicionado.
     * @param digestsListMap Mapa de digests por arquivo a ser atualizado.
     */
    private static void addDigestToList(String fileName, String digestType, String digestHex,
                                        Document doc, Map<String, Map<String, String>> digestsListMap) {
        Element root = doc.getDocumentElement();
        Element fileEntry = findOrCreateFileEntry(doc, root, fileName);

        // Verificar se já existe um digest deste tipo para este arquivo
        boolean digestExists = false;
        NodeList digestEntries = fileEntry.getElementsByTagName("DIGEST_ENTRY");
        for (int i = 0; i < digestEntries.getLength(); i++) {
            Element digestEntry = (Element) digestEntries.item(i);
            String type = digestEntry.getElementsByTagName("DIGEST_TYPE").item(0).getTextContent();
            if (type.equals(digestType)) {
                digestExists = true;
                break;
            }
        }

        if (!digestExists) {
            Element digestEntry = doc.createElement("DIGEST_ENTRY");
            Element digestTypeElement = doc.createElement("DIGEST_TYPE");
            digestTypeElement.appendChild(doc.createTextNode(digestType));
            Element digestHexElement = doc.createElement("DIGEST_HEX");
            digestHexElement.appendChild(doc.createTextNode(digestHex));

            digestEntry.appendChild(digestTypeElement);
            digestEntry.appendChild(digestHexElement);
            fileEntry.appendChild(digestEntry);

            // Atualizar o mapa
            if (!digestsListMap.containsKey(fileName)) {
                digestsListMap.put(fileName, new HashMap<>());
            }
            digestsListMap.get(fileName).put(digestType, digestHex);
        }
    }

    /**
     * Encontra ou cria uma entrada de arquivo no documento XML.
     *
     * @param doc Documento XML.
     * @param root Elemento raiz do documento.
     * @param fileName Nome do arquivo a ser encontrado/criado.
     * @return Elemento FILE_ENTRY correspondente ao arquivo.
     */
    private static Element findOrCreateFileEntry(Document doc, Element root, String fileName) {
        NodeList fileEntries = root.getElementsByTagName("FILE_ENTRY");

        for (int i = 0; i < fileEntries.getLength(); i++) {
            Element fileEntry = (Element) fileEntries.item(i);
            String currentFileName = fileEntry.getElementsByTagName("FILE_NAME").item(0).getTextContent();
            if (currentFileName.equals(fileName)) {
                return fileEntry;
            }
        }

        Element fileEntry = doc.createElement("FILE_ENTRY");
        Element fileNameElement = doc.createElement("FILE_NAME");
        fileNameElement.appendChild(doc.createTextNode(fileName));
        fileEntry.appendChild(fileNameElement);
        root.appendChild(fileEntry);

        return fileEntry;
    }

    /**
     * Salva a lista de digests em um arquivo XML.
     *
     * @param doc Documento XML a ser salvo.
     * @param path Caminho do arquivo de destino.
     * @throws Exception Se ocorrer erro durante o salvamento.
     */
    private static void saveDigestList(Document doc, String path) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(path));

        transformer.transform(source, result);
    }
}