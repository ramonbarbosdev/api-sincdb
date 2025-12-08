package com.api_sincdb.domain.usuario.service;


import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.api_sincdb.domain.usuario.model.Anexo;
import com.api_sincdb.domain.usuario.repository.AnexoRepository;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class AnexoService {

    private final S3Client s3;
    public final String bucket = "anexoagro";
    private final String acess_key = "DO00N7WJMK8T6QHWVPB9";
    private final String secret_key = "yBr0ikm5r2WXiCJqK6TbkpHQbPJfGUw+MV2n06J3Qr0";

    @Autowired
    private AnexoRepository anexoRepository;

    public AnexoService() {
        this.s3 = S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create("https://sfo3.digitaloceanspaces.com"))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(this.acess_key, this.secret_key)))
                .build();
    }


    
    public String uploadFoto(Long id,  String caminhoImage, MultipartFile file ) throws IOException {
            
            deletarPorIdModel(id, caminhoImage);

            Anexo anexo = salvarModelAnexo(caminhoImage, id, file);

            String bucketUrl = "https://" + bucket + ".sfo3.digitaloceanspaces.com/";
            String urlImagem = anexo.getNm_model() + "/" + anexo.getId_model() + "/" + anexo.getNm_anexo();
            String urlCompleta = bucketUrl + urlImagem;

            return urlCompleta;
    }

    

    public String upload(MultipartFile file, String nm_model, Long id, Optional<Long> optionalId) throws IOException {

        Long id_detalhe = optionalId.orElse(null);

        String key = nm_model + "/" + id + "/" + file.getOriginalFilename();

        if (id_detalhe != null) {
            key = nm_model + "/" + id + "/" + id_detalhe + "/" + file.getOriginalFilename();
        }

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .acl(ObjectCannedACL.PUBLIC_READ)
                .build();

        s3.putObject(request, software.amazon.awssdk.core.sync.RequestBody.fromBytes(file.getBytes()));

        return "https://" + bucket + ".nyc3.digitaloceanspaces.com/" + key;
    }

    public byte[] download(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        return s3.getObjectAsBytes(getObjectRequest).asByteArray();
    }

    public void delete(String key) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        s3.deleteObject(deleteObjectRequest);
    }

    public Anexo salvarModelAnexo(String nm_model, Long id_model, MultipartFile file) throws IOException {
        String url = upload(file, nm_model, id_model, Optional.empty());

        Anexo anexo = new Anexo();
        anexo.setNm_model(nm_model);
        anexo.setId_model(id_model);
        anexo.setNm_anexo(file.getOriginalFilename());
        anexo.setContent_type(file.getContentType());
        anexo.setNu_tamanho(file.getSize());
        anexo.setUrl(url);
        anexoRepository.save(anexo);

        return anexoRepository.save(anexo);
    }

    public String extrairChaveDoUrl(String url) {
        // Exemplo: https://meu-bucket.nyc3.digitaloceanspaces.com/pedido/123/nota.pdf
        // chave = pedido/123/nota.pdf
        return url.substring(url.indexOf(".com/") + 5);
    }

    public void deletarPorIdModel(Long idModel, String NmModel) {
        List<Anexo> anexo = anexoRepository.findByIdModelByNmModel(idModel, NmModel);

        if(anexo == null)  return;

        for (Anexo item : anexo) {
            delete(extrairChaveDoUrl(item.getUrl()));

            anexoRepository.deleteById(item.getId_anexo());
        }

    }

    public void deletarPorIdAnexo(Long anexoId) {
        Anexo anexo = anexoRepository.findById(anexoId)
                .orElseThrow(() -> new RuntimeException("Anexo não encontrado"));

        delete(extrairChaveDoUrl(anexo.getUrl()));

        anexoRepository.deleteById(anexoId);
    }

    public void excluirAnexoLote(Long id, String model) throws Exception {

        List<Anexo> listagemAnexo = anexoRepository.findByModelByIdModel(model, id);

        if (listagemAnexo.size() == 0)
            return;

        for (Anexo anexo : listagemAnexo) {

            deletarPorIdAnexo(anexo.getId_anexo());
        }

    }

}
