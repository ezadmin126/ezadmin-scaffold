package top.ezadmin.scaffold.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import top.ezadmin.scaffold.domain.Asset;
import top.ezadmin.scaffold.service.AssetService;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping(AssetController.PATH)
public class AssetController {
    static final String PATH = "/api/native/assets";
    private final AssetService service;

    public AssetController(AssetService service) {
        this.service = service;
    }

    @GetMapping
    public List<Asset> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public Asset get(@PathVariable long id) {
        return requireAsset(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<Asset> create(@RequestBody AssetInput input) {
        Asset asset = service.create(input.name(), input.notes());
        return ResponseEntity.created(URI.create(PATH + "/" + asset.getId())).body(asset);
    }

    @PutMapping("/{id}")
    public Asset update(@PathVariable long id, @RequestBody AssetInput input) {
        return requireAsset(service.update(id, input.name(), input.notes()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        if (!service.delete(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalidInput(IllegalArgumentException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    private Asset requireAsset(Optional<Asset> asset) {
        return asset.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public record AssetInput(String name, String notes) {
    }
}
