# PyExtensionSetup
Autoinstall Python and requirements on G-Python extension launch

## How to use
Include the [jar file](https://github.com/WiredSpast/PyExtensionSetup/releases/latest) in your extension.zip and set the command as the following:
```cmd
java -jar PyExtensionSetup.jar -e {extensionScript} -v {minimalPythonVersion} -c {cookie} -p {port} -f {filename}
```
Example:
```cmd
java -jar PyExtensionSetup.jar -e myExtension.py -v 3.2.0 -c {cookie} -p {port} -f {filename}
```
How to note in extension.json:
```json
"commands": {
  "default": ["java", "-jar", "PyExtensionSetup.jar", "-e", "myExtension.py", "-v", "3.2.0", "-c", "{cookie}", "-p", "{port}", "-f", "{filename}"]
}
```

### Setting up python requirements
Include a requirements.txt file which lists all the required pip installations!
(Make sure to also include G-Python)
