import os

files_to_update = {
    'src/main/java/com/fabricmanagement/common/infrastructure/security/JwtAuthenticationFilter.java': [
        ('downstream security beans (e.g. ProductionAccessService)', 'downstream security checks (e.g. PermissionEvaluator)')
    ],
    'src/main/java/com/fabricmanagement/common/infrastructure/security/AuthenticatedUserContext.java': [
        ('Consumed by security services (e.g. {@code ProductionAccessService}) via', 'Consumed by security services (e.g. {@code PermissionEvaluator}) via')
    ],
    'src/main/java/com/fabricmanagement/production/masterdata/fiber/api/controller/FiberController.java': [
        ('{@code ProductionAccessService}', '{@code PermissionEvaluator}')
    ],
    'src/main/java/com/fabricmanagement/production/masterdata/material/api/controller/MaterialController.java': [
        ('{@code ProductionAccessService}', '{@code PermissionEvaluator}')
    ],
    'src/main/java/com/fabricmanagement/production/execution/batch/api/controller/BatchController.java': [
        ('{@code ProductionAccessService}', '{@code PermissionEvaluator}')
    ],
    'src/main/java/com/fabricmanagement/production/quality/result/api/controller/FiberTestResultController.java': [
        ('{@code ProductionAccessService}', '{@code PermissionEvaluator}')
    ],
    'src/main/java/com/fabricmanagement/flowboard/board/app/BoardService.java': [
        ('FlowBoardAccessService tests logic against SecurityContext usually, but we need', 'PermissionEvaluator tests logic against SecurityContext usually, but we need'),
        ('USER can write/read this using FlowBoardAccessService', 'USER can write/read this using PermissionEvaluator'),
        ('Sadece aynı tenant, aktif kullanıcılar. FlowBoardAccessService', 'Sadece aynı tenant, aktif kullanıcılar. PermissionEvaluator')
    ]
}

base_dir = '.'
for filepath, replacements in files_to_update.items():
    full_path = os.path.join(base_dir, filepath)
    if not os.path.exists(full_path):
        print(f"Skipping {filepath}, file not found")
        continue

    with open(full_path, 'r') as f:
        content = f.read()

    changed = False
    for old_str, new_str in replacements:
        if old_str in content:
            content = content.replace(old_str, new_str)
            changed = True

    if changed:
        with open(full_path, 'w') as f:
            f.write(content)
        print(f"Updated {filepath}")

print("Comment cleanup finished.")
