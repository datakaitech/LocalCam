using System.IO;
using System.Text.Json;
namespace LocalCam;

// Immutable snapshots keep the upload worker and UI consistent.
internal sealed record OutputSettings(string Aspect = "Source", string Framing = "Fit", bool Mirror = false, int Rotation = 0, double Zoom = 1) {
    public int SchemaVersion => 2;
    public static readonly OutputSettings Default = new();
    public bool IsDefault => Aspect == "Source" && !Mirror && Rotation == 0 && Zoom == 1;
    public bool IsValid => Aspect is "Source" or "16:9" or "4:3" or "1:1" or "9:16"
        && Framing is "Fit" or "Fill" && Rotation is 0 or 90 or 180 or 270
        && double.IsFinite(Zoom) && Zoom >= 1 && Zoom <= 3;
}
internal sealed class OutputSettingsStore {
    private readonly string? path;
    public string? Error { get; private set; }
    public OutputSettingsStore(string? path) => this.path = path;
    public OutputSettings Load() {
        if (path == null || !File.Exists(path)) return OutputSettings.Default;
        try {
            string json = File.ReadAllText(path);
            var settings = JsonSerializer.Deserialize<OutputSettings>(json);
            if (settings?.IsValid == true) {
                using var document = JsonDocument.Parse(json);
                if (!document.RootElement.TryGetProperty("SchemaVersion", out var version) || version.GetInt32() != 2) {
                    settings = settings with { Aspect = "Source", Framing = "Fit", Zoom = 1 };
                    Save(settings);
                }
                return settings;
            }
            Error = "Saved output settings were invalid; defaults restored.";
        } catch { Error = "Could not read saved output settings; defaults restored."; }
        return OutputSettings.Default;
    }
    public void Save(OutputSettings settings) {
        if (path == null) return;
        try {
            File.WriteAllText(path + ".tmp", JsonSerializer.Serialize(settings, new JsonSerializerOptions { WriteIndented = true }));
            File.Move(path + ".tmp", path, true);
            Error = null;
        } catch { Error = "Settings apply now, but could not be saved beside the application."; }
    }
}
