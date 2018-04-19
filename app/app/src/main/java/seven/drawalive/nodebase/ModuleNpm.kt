package seven.drawalive.nodebase

object ModuleNpm {
    fun InstallNpmFromZip(zipfile: String, target_dir: String) {
        Storage.unzip(zipfile, target_dir)
    }
}
