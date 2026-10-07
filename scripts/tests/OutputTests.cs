using System.IO;
using LocalCam;
static class OutputTests {
    static void Check(bool result,string message){if(!result)throw new Exception(message);}
    static void Main(){
        Check(!new OutputSettings(Zoom:double.NaN).IsValid&&!new OutputSettings(Rotation:45).IsValid&&!new OutputSettings(Aspect:"invalid").IsValid,"invalid settings");
        string file=Path.Combine(AppContext.BaseDirectory,"settings-test-"+Guid.NewGuid()+".json");
        try{
            var store=new OutputSettingsStore(file);var expected=new OutputSettings("16:9","Fill",true,270,1.5);
            store.Save(expected);Check(store.Load()==expected,"persisted settings");
            File.WriteAllText(file,"{\"Aspect\":\"16:9\",\"Framing\":\"Fill\",\"Mirror\":true,\"Rotation\":270,\"Zoom\":2}");
            var migrated=expected with {Aspect="Source",Framing="Fit",Zoom=1};
            Check(store.Load()==migrated,"legacy crop reset preserves orientation controls");
            Check(store.Load()==migrated&&File.ReadAllText(file).Contains("SchemaVersion"),"migration saved once");
            store.Save(expected);Check(store.Load()==expected,"explicit crop preserved after migration");
            File.WriteAllText(file,"{invalid");Check(store.Load()==OutputSettings.Default&&store.Error!=null,"corrupt file recovery");
            File.WriteAllText(file,"{\"Zoom\":99}");Check(store.Load()==OutputSettings.Default,"out-of-range recovery");
            store.Save(OutputSettings.Default);Check(store.Load()==OutputSettings.Default&&store.Error==null,"reset and save recover");
        }finally{if(File.Exists(file))File.Delete(file);}
        Console.WriteLine("PASS: saved output preferences, reset, validation and invalid-file recovery.");
    }
}
