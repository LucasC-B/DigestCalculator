package org.T2INF1416;

import java.security.*;
import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.*;
import org.xml.sax.*;

public class DigestCalculator {
    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Uso: DigestCalculator <Tipo_Digest> <Caminho_da_Pasta> <Caminho_ArqListaDigest>");
            System.out.println("Tipos de digest suportados: MD5, SHA1, SHA256, SHA512");
            System.exit(1);
        }

        String digestType = args[0].toUpperCase();
        String folderPath = args[1];
        String digestListPath = args[2];

        try {
            // Verificar se o tipo de digest é suportado
            if (!isDigestSupported(digestType)) {
                System.out.println("Tipo de digest não suportado: " + digestType);
                System.exit(1);
            }

            // Carregar a lista de digests conhecidos
            Document digestList = loadDigestList(digestListPath);
            Map<String, Set<String>> fileDigestsMap = buildFileDigestsMap(digestList);
            Map<String, Set<String>> digestFilesMap = buildDigestFilesMap(digestList);

            // Processar cada arquivo na pasta
            File folder = new File(folderPath);
            File[] files = folder.listFiles();

            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        processFile(file, digestType, digestList, fileDigestsMap, digestFilesMap);
                    }
                }
            }

            // Salvar a lista atualizada de digests
            saveDigestList(digestList, digestListPath);

        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static boolean isDigestSupported(String digestType) {
        return digestType.equals("MD5") || digestType.equals("SHA1") ||
                digestType.equals("SHA256") || digestType.equals("SHA512");
    }

    private static Map<String, Set<String>> buildFileDigestsMap(Document doc) {
        Map<String, Set<String>> map = new HashMap<>();
        NodeList fileEntries = doc.getElementsByTagName("FILE_ENTRY");

        for (int i = 0; i < fileEntries.getLength(); i++) {
            Element fileEntry = (Element) fileEntries.item(i);
            String fileName = fileEntry.getElementsByTagName("FILE_NAME").item(0).getTextContent();

            Set<String> digests = new HashSet<>();
            NodeList digestEntries = fileEntry.getElementsByTagName("DIGEST_ENTRY");

            for (int j = 0; j < digestEntries.getLength(); j++) {
                Element digestEntry = (Element) digestEntries.item(j);
                String digestHex = digestEntry.getElementsByTagName("DIGEST_HEX").item(0).getTextContent();
                digests.add(digestHex);
            }

            map.put(fileName, digests);
        }

        return map;
    }

    private static Map<String, Set<String>> buildDigestFilesMap(Document doc) {
        Map<String, Set<String>> map = new HashMap<>();
        NodeList fileEntries = doc.getElementsByTagName("FILE_ENTRY");

        for (int i = 0; i < fileEntries.getLength(); i++) {
            Element fileEntry = (Element) fileEntries.item(i);
            String fileName = fileEntry.getElementsByTagName("FILE_NAME").item(0).getTextContent();

            NodeList digestEntries = fileEntry.getElementsByTagName("DIGEST_ENTRY");

            for (int j = 0; j < digestEntries.getLength(); j++) {
                Element digestEntry = (Element) digestEntries.item(j);
                String digestHex = digestEntry.getElementsByTagName("DIGEST_HEX").item(0).getTextContent();

                if (!map.containsKey(digestHex)) {
                    map.put(digestHex, new HashSet<>());
                }
                map.get(digestHex).add(fileName);
            }
        }

        return map;
    }

    private static void processFile(File file, String digestType, Document digestList,
                                    Map<String, Set<String>> fileDigestsMap,
                                    Map<String, Set<String>> digestFilesMap) throws Exception {
        String fileName = file.getName();
        String calculatedDigest = calculateFileDigest(file, digestType);

        String status = determineStatus(fileName, digestType, calculatedDigest, fileDigestsMap, digestFilesMap);

        System.out.println(fileName + " " + digestType + " " + calculatedDigest + " " + status);

        if (status.equals("NOT FOUND")) {
            addDigestToList(fileName, digestType, calculatedDigest, digestList, fileDigestsMap, digestFilesMap);
        }
    }

    private static String determineStatus(String fileName, String digestType, String calculatedDigest,
                                          Map<String, Set<String>> fileDigestsMap,
                                          Map<String, Set<String>> digestFilesMap) {
        // Verificar colisão primeiro
        if (digestFilesMap.containsKey(calculatedDigest)) {
            Set<String> filesWithSameDigest = digestFilesMap.get(calculatedDigest);
            if (!filesWithSameDigest.contains(fileName)) {
                return "COLISION";
            }
        }

        // Verificar se o arquivo está na lista
        if (fileDigestsMap.containsKey(fileName)) {
            Set<String> knownDigests = fileDigestsMap.get(fileName);
            if (knownDigests.contains(calculatedDigest)) {
                return "OK";
            } else {
                return "NOT OK";
            }
        }

        return "NOT FOUND";
    }

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

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    private static Document loadDigestList(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        try {
            File file = new File(path);
            if (file.exists() && file.length() > 0) {
                return builder.parse(file);
            } else {
                // Criar novo documento se o arquivo não existir ou estiver vazio
                Document doc = builder.newDocument();
                Element root = doc.createElement("CATALOG");
                doc.appendChild(root);
                return doc;
            }
        } catch (SAXException | IOException e) {
            System.err.println("Erro ao carregar o arquivo XML. Criando novo documento.");
            Document doc = builder.newDocument();
            Element root = doc.createElement("CATALOG");
            doc.appendChild(root);
            return doc;
        }
    }

    private static void addDigestToList(String fileName, String digestType, String digestHex,
                                        Document doc, Map<String, Set<String>> fileDigestsMap,
                                        Map<String, Set<String>> digestFilesMap) {
        Element root = doc.getDocumentElement();

        // Encontrar ou criar FILE_ENTRY para este arquivo
        Element fileEntry = findOrCreateFileEntry(doc, root, fileName);

        // Criar nova DIGEST_ENTRY
        Element digestEntry = doc.createElement("DIGEST_ENTRY");

        Element digestTypeElement = doc.createElement("DIGEST_TYPE");
        digestTypeElement.appendChild(doc.createTextNode(digestType));

        Element digestHexElement = doc.createElement("DIGEST_HEX");
        digestHexElement.appendChild(doc.createTextNode(digestHex));

        digestEntry.appendChild(digestTypeElement);
        digestEntry.appendChild(digestHexElement);
        fileEntry.appendChild(digestEntry);

        // Atualizar os maps
        if (!fileDigestsMap.containsKey(fileName)) {
            fileDigestsMap.put(fileName, new HashSet<>());
        }
        fileDigestsMap.get(fileName).add(digestHex);

        if (!digestFilesMap.containsKey(digestHex)) {
            digestFilesMap.put(digestHex, new HashSet<>());
        }
        digestFilesMap.get(digestHex).add(fileName);
    }

    private static Element findOrCreateFileEntry(Document doc, Element root, String fileName) {
        NodeList fileEntries = root.getElementsByTagName("FILE_ENTRY");

        // Procurar FILE_ENTRY existente para este arquivo
        for (int i = 0; i < fileEntries.getLength(); i++) {
            Element fileEntry = (Element) fileEntries.item(i);
            String currentFileName = fileEntry.getElementsByTagName("FILE_NAME").item(0).getTextContent();
            if (currentFileName.equals(fileName)) {
                return fileEntry;
            }
        }

        // Criar novo FILE_ENTRY se não existir
        Element fileEntry = doc.createElement("FILE_ENTRY");

        Element fileNameElement = doc.createElement("FILE_NAME");
        fileNameElement.appendChild(doc.createTextNode(fileName));

        fileEntry.appendChild(fileNameElement);
        root.appendChild(fileEntry);

        return fileEntry;
    }

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